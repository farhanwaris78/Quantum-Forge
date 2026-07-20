/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #116 (builder slice): fail-closed builder for a Materials Project
 * mp-api v2 {@code GET /materials/summary/} document URL. Like the OPTIMADE
 * builder (batch 100) this class performs NO network access - it validates
 * the base and material ids, composes the URL, and returns it for review.
 *
 * <p>Owned subset (stated so the boundary is auditable):</p>
 * <ul>
 *   <li>base URL must be https (Materials Project is TLS-only), with a
 *       host, no userinfo, no query, no fragment; the path is normalized to
 *       the API root (empty or "/");</li>
 *   <li>material ids must match the documented task-id forms
 *       {@code mp-NUMBER} or {@code mvc-NUMBER} (1..10 digits, 1..20 ids,
 *       order preserved exactly as entered - no reordering, no deduping:
 *       the analyst owns the list);</li>
 *   <li>the API key is NEVER attached to the URL and never echoed back -
 *       the draft names the {@code X-API-KEY} header and reports only
 *       "provided (N chars)" or "NOT provided"; keys containing whitespace
 *       or line-breaks are refused (header-injection guard);</li>
 *   <li>route forwards: only /materials/summary/ with the material_ids
 *       parameter - POST-based search, _fields projection, and JSON
 *       response parsing are runtime depth.</li>
 * </ul>
 *
 * <p>Honesty: route and parameter semantics follow the mp-api v2
 * documentation as curated here; the actual fetch, TLS, rate-limits and
 * deprecation behavior live in the #116 runtime depth. The GUI writes the
 * draft only via the explicit save action.</p>
 *
 * <p>Refusal codes: MP_BASE, MP_ID, MP_KEY, MP_VALUE.</p>
 */
public final class MpApiQueryBuilder {

    /** Owned caps. */
    public static final int MAX_IDS = 20;
    private static final Pattern TASK_ID = Pattern.compile("(mp|mvc)-[0-9]{1,10}");

    /** The composed, still-unfetched query. */
    public static final class MpQuery {
        private final String normalizedBase;
        private final List<String> materialIds;
        private final String url;
        private final boolean apiKeyProvided;

        MpQuery(String normalizedBase, List<String> materialIds, String url,
                boolean apiKeyProvided) {
            this.normalizedBase = normalizedBase;
            this.materialIds = new ArrayList<>(materialIds);
            this.url = url;
            this.apiKeyProvided = apiKeyProvided;
        }

        public String getNormalizedBase() { return this.normalizedBase; }
        /** Exactly the order the analyst entered; never reordered. */
        public List<String> getMaterialIds() { return List.copyOf(this.materialIds); }
        /** Full GET URL - NOT fetched by this slice; carries no key material. */
        public String getUrl() { return this.url; }
        /** True when an API key was handed over for the X-API-KEY header. */
        public boolean isApiKeyProvided() { return this.apiKeyProvided; }
    }

    private MpApiQueryBuilder() {
    }

    /**
     * Builds the query. Codes: MP_BASE, MP_ID, MP_KEY, MP_VALUE.
     */
    public static OperationResult<MpQuery> build(String baseUrl, String materialIdsCsv,
            String apiKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return OperationResult.failed("MP_BASE",
                    "A Materials Project API base URL is required.", null);
        }
        URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (URISyntaxException ex) {
            return OperationResult.failed("MP_BASE",
                    "The base URL does not parse: " + ex.getMessage(), null);
        }
        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https")) {
            return OperationResult.failed("MP_BASE",
                    "Materials Project is TLS-only: the base URL must be https "
                            + "(got '" + uri.getScheme() + "').",
                    null);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return OperationResult.failed("MP_BASE",
                    "The base URL holds no host.", null);
        }
        if (uri.getUserInfo() != null) {
            return OperationResult.failed("MP_BASE",
                    "Credentials in the URL are refused - the API key travels in "
                            + "the X-API-KEY header, never in a logged URL.",
                    null);
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return OperationResult.failed("MP_BASE",
                    "The base URL must not carry a query string or fragment.",
                    null);
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.isEmpty() && !path.equals("/")) {
            return OperationResult.failed("MP_BASE",
                    "The builder owns the API root only (path must be empty or "
                            + "'/'); got '" + path + "'.",
                    null);
        }
        String normalized = "https://" + uri.getHost().toLowerCase(
                java.util.Locale.ROOT) + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");

        if (materialIdsCsv == null || materialIdsCsv.isBlank()) {
            return OperationResult.failed("MP_ID",
                    "At least one material id (mp-NUMBER or mvc-NUMBER) is required.",
                    null);
        }
        String[] tokens = materialIdsCsv.trim().split("[,\\s]+");
        if (tokens.length > MAX_IDS) {
            return OperationResult.failed("MP_ID",
                    "The builder caps a summary query at " + MAX_IDS
                            + " material id(s) (got " + tokens.length + ").",
                    null);
        }
        List<String> ids = new ArrayList<>();
        for (String token : tokens) {
            if (!TASK_ID.matcher(token).matches()) {
                return OperationResult.failed("MP_ID",
                        "'" + token + "' is not a documented task id (mp-NUMBER or "
                                + "mvc-NUMBER) - it is NOT interpolated into the "
                                + "URL.",
                        null);
            }
            ids.add(token);
        }
        boolean keyProvided = apiKey != null && !apiKey.isBlank();
        if (keyProvided) {
            for (int idx = 0; idx < apiKey.length(); idx += 1) {
                char ch = apiKey.charAt(idx);
                if (Character.isWhitespace(ch)) {
                    return OperationResult.failed("MP_KEY",
                            "The API key contains whitespace/line-breaks - refused "
                                    + "(header-injection guard). Paste the plain "
                                    + "token only.",
                            null);
                }
            }
        }
        StringBuilder url = new StringBuilder()
                .append(normalized).append("/materials/summary/?material_ids=");
        for (int idx = 0; idx < ids.size(); idx += 1) {
            if (idx > 0) {
                url.append(',');
            }
            url.append(ids.get(idx));
        }
        return OperationResult.success("MP_OK", "Built the summary query URL.",
                new MpQuery(normalized, ids, url.toString(), keyProvided));
    }
}
