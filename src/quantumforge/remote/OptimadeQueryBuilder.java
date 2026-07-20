/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #117 (query-builder slice): fail-closed builder for an OPTIMADE
 * v1 {@code GET /structures?filter=...} query URL. This class performs NO
 * network access - it validates the provider base URL, composes a bounded
 * element-driven filter subset, percent-encodes it, and returns the exact
 * URL plus the human-readable clauses for review.
 *
 * <p>Owned subset (stated so the boundary is auditable):</p>
 * <ul>
 *   <li>base URL must be http(s) with a host, no userinfo, no query, no
 *       fragment, and a path ending in {@code /v1} - the v1 API root;</li>
 *   <li>filter = {@code elements HAS ALL "A","B"} (1..8 whitelist-checked
 *       element symbols, REQUIRED) optionally ANDed with {@code nelements<=N}
 *       and {@code nsites<=M} (positive caps, analyst-supplied); free-form
 *       OPTIMADE filter grammar is NOT parsed here - building beats parsing
 *       for injection safety;</li>
 *   <li>pagination = page_limit (1..1000, default 20) + page_offset 0;</li>
 *   <li>endpoint is /structures ONLY (references and JSON:API response
 *       parsing are runtime depth).</li>
 * </ul>
 *
 * <p>Nothing is fetched: the network fetch, TLS handling, JSON:API parsing
 * and the provider-capability negotiation (e.g. which OPTIONAL fields like
 * band_gap a given provider indexes) all remain the remaining #117 depth.
 * The draft GUI writes nothing until the explicit save/run action.</p>
 *
 * <p>Refusal codes: OPTIMADE_BASE, OPTIMADE_VALUE, OPTIMADE_ELEMENT.</p>
 */
public final class OptimadeQueryBuilder {

    /** Element-driven builder caps. */
    public static final int MAX_ELEMENTS = 8;
    public static final int MAX_PAGE_LIMIT = 1000;
    public static final int DEFAULT_PAGE_LIMIT = 20;
    public static final int MAX_SITE_CAP = 100_000;

    private static final Pattern ELEMENT = Pattern.compile("[A-Z][a-z]?");

    /** The composed, still-unfetched query. */
    public static final class OptimadeQuery {
        private final String normalizedBase;
        private final String url;
        private final String filter;
        private final List<String> clauses;
        private final int pageLimit;

        OptimadeQuery(String normalizedBase, String url, String filter,
                List<String> clauses, int pageLimit) {
            this.normalizedBase = normalizedBase;
            this.url = url;
            this.filter = filter;
            this.clauses = new ArrayList<>(clauses);
            this.pageLimit = pageLimit;
        }

        /** Base URL normalized (no trailing slash, /v1 path enforced). */
        public String getNormalizedBase() { return this.normalizedBase; }
        /** The full GET URL - NOT fetched by this slice. */
        public String getUrl() { return this.url; }
        /** The human-readable filter before encoding. */
        public String getFilter() { return this.filter; }
        /** One entry per ANDed clause. */
        public List<String> getClauses() { return List.copyOf(this.clauses); }
        public int getPageLimit() { return this.pageLimit; }
    }

    private OptimadeQueryBuilder() {
    }

    /**
     * Builds the query. Codes: OPTIMADE_BASE, OPTIMADE_VALUE,
     * OPTIMADE_ELEMENT.
     */
    public static OperationResult<OptimadeQuery> build(String baseUrl, String elements,
            int nelementsMax, int nsitesMax, int pageLimit) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return OperationResult.failed("OPTIMADE_BASE",
                    "An OPTIMADE provider base URL is required.", null);
        }
        URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (URISyntaxException ex) {
            return OperationResult.failed("OPTIMADE_BASE",
                    "The base URL does not parse: " + ex.getMessage(), null);
        }
        if (uri.getScheme() == null
                || !(uri.getScheme().equalsIgnoreCase("https")
                        || uri.getScheme().equalsIgnoreCase("http"))) {
            return OperationResult.failed("OPTIMADE_BASE",
                    "The base URL must be http(s) (got '" + uri.getScheme() + "').",
                    null);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return OperationResult.failed("OPTIMADE_BASE",
                    "The base URL holds no host.", null);
        }
        if (uri.getUserInfo() != null) {
            return OperationResult.failed("OPTIMADE_BASE",
                    "Credentials in the URL are refused - put provider auth in "
                            + "provider-side configuration, never in a logged URL.",
                    null);
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return OperationResult.failed("OPTIMADE_BASE",
                    "The base URL must not carry a query string or fragment.",
                    null);
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.endsWith("/v1")) {
            return OperationResult.failed("OPTIMADE_BASE",
                    "The base path must end in /v1 (the OPTIMADE v1 API root); got '"
                            + path + "'.",
                    null);
        }
        String normalized = uri.getScheme().toLowerCase(java.util.Locale.ROOT)
                + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT)
                + (uri.getPort() >= 0 ? ":" + uri.getPort() : "") + path;

        if (elements == null || elements.isBlank()) {
            return OperationResult.failed("OPTIMADE_ELEMENT",
                    "At least one element symbol is required - the owned slice "
                            + "builds element-driven queries only.",
                    null);
        }
        String[] tokens = elements.trim().split("[,\\s]+");
        if (tokens.length > MAX_ELEMENTS) {
            return OperationResult.failed("OPTIMADE_ELEMENT",
                    "The builder caps element lists at " + MAX_ELEMENTS + " (got "
                            + tokens.length + ").",
                    null);
        }
        StringBuilder hasAll = new StringBuilder("elements HAS ALL ");
        for (int idx = 0; idx < tokens.length; idx += 1) {
            if (!ELEMENT.matcher(tokens[idx]).matches()) {
                return OperationResult.failed("OPTIMADE_ELEMENT",
                        "'" + tokens[idx] + "' is not an element-style symbol "
                                + "([A-Z][a-z]?) - it is not quoted into the filter "
                                + "blindly.",
                        null);
            }
            if (idx > 0) {
                hasAll.append(',');
            }
            hasAll.append('"').append(tokens[idx]).append('"');
        }
        if (nelementsMax < 0 || nsitesMax < 0 || nelementsMax > MAX_SITE_CAP
                || nsitesMax > MAX_SITE_CAP) {
            return OperationResult.failed("OPTIMADE_VALUE",
                    "nelements/nsites caps must lie in 0.." + MAX_SITE_CAP
                            + " (0 omits the clause).",
                    null);
        }
        if (pageLimit < 0 || pageLimit > MAX_PAGE_LIMIT) {
            return OperationResult.failed("OPTIMADE_VALUE",
                    "page_limit must lie in 0.." + MAX_PAGE_LIMIT + " (0 maps to "
                            + "the default " + DEFAULT_PAGE_LIMIT + ").",
                    null);
        }
        int limit = pageLimit == 0 ? DEFAULT_PAGE_LIMIT : pageLimit;
        List<String> clauses = new ArrayList<>();
        clauses.add(hasAll.toString());
        if (nelementsMax > 0) {
            clauses.add("nelements<=" + nelementsMax);
        }
        if (nsitesMax > 0) {
            clauses.add("nsites<=" + nsitesMax);
        }
        StringBuilder filter = new StringBuilder();
        for (int idx = 0; idx < clauses.size(); idx += 1) {
            if (idx > 0) {
                filter.append(" AND ");
            }
            filter.append(clauses.get(idx));
        }
        String encoded = URLEncoder.encode(filter.toString(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String url = normalized + "/structures?filter=" + encoded + "&page_limit="
                + limit + "&page_offset=0";
        return OperationResult.success("OPTIMADE_OK", "Built the query URL.",
                new OptimadeQuery(normalized, url, filter.toString(), clauses, limit));
    }
}
