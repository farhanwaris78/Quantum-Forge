/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.com.secrets;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import quantumforge.com.log.AppLog;

/**
 * Windows Credential Manager backend via PowerShell DPAPI-protected storage.
 *
 * <p>Secrets are stored as generic credentials named {@code QuantumForge/<key>}
 * using {@code cmdkey} is insufficient for secret payloads, so this uses a
 * PowerShell helper with {@code ConvertFrom-SecureString}/DPAPI bound to the
 * current user. If PowerShell is unavailable, {@link #isAvailable()} is false.</p>
 */
public final class WindowsCredentialBackend implements SecretStore.OsKeyringBackend {

    private static final String PREFIX = "QuantumForge/";
    private final String powershellPath;

    public WindowsCredentialBackend(String powershellPath) {
        this.powershellPath = powershellPath;
    }

    public static WindowsCredentialBackend detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return new WindowsCredentialBackend(null);
        }
        String ps = findOnPath("powershell.exe");
        if (ps == null) {
            ps = findOnPath("pwsh.exe");
        }
        return new WindowsCredentialBackend(ps);
    }

    @Override
    public String name() {
        return isAvailable() ? "windows-credential-manager/dpapi" : "windows-credential-manager (unavailable)";
    }

    @Override
    public boolean isAvailable() {
        return this.powershellPath != null && !this.powershellPath.isBlank();
    }

    @Override
    public void store(String key, char[] secret) {
        if (!isAvailable() || key == null || secret == null) {
            return;
        }
        try {
            String target = PREFIX + sanitize(key);
            String b64 = Base64.getEncoder().encodeToString(new String(secret).getBytes(StandardCharsets.UTF_8));
            String script = "$ErrorActionPreference='Stop';"
                    + "$p=Join-Path $env:LOCALAPPDATA 'QuantumForge/credentials';"
                    + "New-Item -ItemType Directory -Force -Path $p | Out-Null;"
                    + "$f=Join-Path $p (" + psQuote(target) + " + '.dpapi');"
                    + "$sec=ConvertTo-SecureString -String " + psQuote(b64) + " -AsPlainText -Force;"
                    + "$enc=ConvertFrom-SecureString -SecureString $sec;"
                    + "Set-Content -LiteralPath $f -Value $enc -Encoding ASCII;";
            runPowerShell(script, false);
        } catch (Exception ex) {
            AppLog.warn("keyring-win", "store failed: " + ex.getMessage());
            throw new RuntimeException("windows keyring store failed", ex);
        }
    }

    @Override
    public char[] load(String key) {
        if (!isAvailable() || key == null) {
            return null;
        }
        try {
            String target = PREFIX + sanitize(key);
            String script = "$ErrorActionPreference='Stop';"
                    + "$p=Join-Path $env:LOCALAPPDATA 'QuantumForge/credentials';"
                    + "$f=Join-Path $p (" + psQuote(target) + " + '.dpapi');"
                    + "if(-not (Test-Path -LiteralPath $f)){ exit 3 };"
                    + "$enc=Get-Content -LiteralPath $f -Raw;"
                    + "$sec=ConvertTo-SecureString -String $enc;"
                    + "$bstr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec);"
                    + "try { $plain=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr); Write-Output $plain }"
                    + " finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }";
            String out = runPowerShell(script, true);
            if (out == null || out.isBlank()) {
                return null;
            }
            byte[] raw = Base64.getDecoder().decode(out.trim());
            return new String(raw, StandardCharsets.UTF_8).toCharArray();
        } catch (Exception ex) {
            AppLog.warn("keyring-win", "load failed: " + ex.getMessage());
            return null;
        }
    }

    @Override
    public void delete(String key) {
        if (!isAvailable() || key == null) {
            return;
        }
        try {
            String target = PREFIX + sanitize(key);
            String script = "$p=Join-Path $env:LOCALAPPDATA 'QuantumForge/credentials';"
                    + "$f=Join-Path $p (" + psQuote(target) + " + '.dpapi');"
                    + "if(Test-Path -LiteralPath $f){ Remove-Item -LiteralPath $f -Force }";
            runPowerShell(script, true);
        } catch (Exception ex) {
            AppLog.warn("keyring-win", "delete failed: " + ex.getMessage());
        }
    }

    private String runPowerShell(String script, boolean allowNonZero) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(this.powershellPath);
        command.add("-NoProfile");
        command.add("-NonInteractive");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-Command");
        command.add(script);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(line);
            }
        }
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("powershell timed out");
        }
        if (process.exitValue() != 0 && !allowNonZero) {
            throw new IllegalStateException("powershell exit " + process.exitValue() + ": " + out);
        }
        if (process.exitValue() == 3) {
            return null; // not found
        }
        return out.toString();
    }

    private static String sanitize(String key) {
        return key.replaceAll("[^A-Za-z0-9._@-]", "_");
    }

    private static String psQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (dir == null || dir.isBlank()) {
                continue;
            }
            java.nio.file.Path candidate = java.nio.file.Path.of(dir, name);
            try {
                if (java.nio.file.Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            } catch (SecurityException ignored) {
            }
        }
        return null;
    }
}
