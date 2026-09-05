package service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.prefs.Preferences;

/**
 * Manages the End User License Agreement (EULA) and Software Resource Usage Policy.
 * Protects authorship attribution ("Developed by Sk Mirajul Islam") and ensures
 * the user accepts the policy before accessing the development environment.
 */
public final class PolicyAgreementService {

    private static final String PREF_NODE = "com.auraorbit.policy";
    private static final String KEY_ACCEPTED = "policy_accepted";
    private static final String KEY_TIMESTAMP = "policy_timestamp";
    private static final String KEY_VERSION = "policy_version";
    private static final String KEY_SIGNATURE = "policy_signature";

    public static final String CURRENT_POLICY_VERSION = "2.0.0";
    public static final String DEVELOPER_ATTRIBUTION = "Developed by Sk Mirajul Islam";

    private static final String SECRET_SALT = "AuraOrbit_Integrity_SkMirajulIslam_2026";

    private PolicyAgreementService() {}

    /**
     * Checks if the user has formally accepted the current version of the EULA
     * and verifies that the stored signature has not been tampered with.
     */
    public static boolean isPolicyAccepted() {
        try {
            Preferences prefs = Preferences.userRoot().node(PREF_NODE);
            boolean accepted = prefs.getBoolean(KEY_ACCEPTED, false);
            if (!accepted) {
                return false;
            }
            String version = prefs.get(KEY_VERSION, "");
            if (!CURRENT_POLICY_VERSION.equals(version)) {
                return false;
            }
            long timestamp = prefs.getLong(KEY_TIMESTAMP, 0L);
            String storedSignature = prefs.get(KEY_SIGNATURE, "");
            String expectedSignature = computeSignature(timestamp, version);
            return storedSignature.equals(expectedSignature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Records acceptance of the agreement with an immutable cryptographic signature.
     */
    public static void recordPolicyAcceptance() {
        try {
            Preferences prefs = Preferences.userRoot().node(PREF_NODE);
            long now = System.currentTimeMillis();
            String signature = computeSignature(now, CURRENT_POLICY_VERSION);

            prefs.putBoolean(KEY_ACCEPTED, true);
            prefs.putLong(KEY_TIMESTAMP, now);
            prefs.put(KEY_VERSION, CURRENT_POLICY_VERSION);
            prefs.put(KEY_SIGNATURE, signature);
            prefs.flush();
        } catch (Exception exception) {
            System.err.println("Could not persist policy acceptance: " + exception.getMessage());
        }
    }

    private static String computeSignature(long timestamp, String version) {
        try {
            String payload = timestamp + ":" + version + ":" + DEVELOPER_ATTRIBUTION + ":" + SECRET_SALT;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(timestamp ^ version.hashCode());
        }
    }

    public static String getPolicyTitle() {
        return "Terms of Service & Resource Usage Policy";
    }

    public static String getPolicySummary() {
        return """
        Welcome to AuraOrbit!

        Developed exclusively by Sk Mirajul Islam.
        All rights reserved.

        Before using this application, you must agree to the End User License Agreement
        and Software Resource Usage Policy:

        1. Authorship & Intellectual Property:
           AuraOrbit is authored and copyrighted by Sk Mirajul Islam. Unauthorized
           modification, reverse-engineering, repackaging, or removing attribution
           credits is strictly prohibited.

        2. CPU & Process Execution Policy:
           Terminal commands and compilation/run steps execute locally on your machine
           using operating system subprocesses. Code analysis is debounced to avoid
           unnecessary CPU load.

        3. Memory & Storage Safety:
           AuraOrbit uses virtualized rendering for minimal RAM footprint and atomic
           file saves (.tmp staging + .bak backup) to protect against data loss.

        4. Security & Privacy:
           No telemetry or background code tracking is collected. Collaborative
           connections require your explicit initiation, and AI API keys are stored
           locally in your operating system's secure preferences.

        By clicking "Accept & Continue", you confirm that you have read and agree to be
        legally bound by these terms.
        """;
    }
}
