package security

/**
 * GitLeaksScanner Class
 * Provides functionality for scanning repositories for credential leaks
 */
class GitLeaksScanner {
    /**
     * Perform a GitLeaks security scan on a given repository
     * 
     * @param repoUrl URL of the repository to scan
     * @param configPath Path to the GitLeaks configuration file (optional)
     * @param reportPath Path where the scan report will be generated
     * @param verbose Enable verbose logging
     * @return boolean indicating scan success or failure
     */
    static boolean scan(String repoUrl, String configPath, String reportPath, boolean verbose = false) {
        // Create a temporary directory for cloning
        def cloneDir = File.createTempDir("gitleaks-scan-", "")
        
        try {
            // Clone the repository
            println "[INFO] Cloning repo: ${repoUrl} to ${cloneDir.absolutePath}"
            if (!cloneRepository(repoUrl, cloneDir.absolutePath)) {
                return false
            }

            println "[INFO] Clone successful. Starting GitLeaks scan..."

            // Run GitLeaks scan
            return runGitLeaksScan(cloneDir.absolutePath, configPath, reportPath, verbose)
        } catch (Exception e) {
            println "[ERROR] Exception occurred during scan: ${e.message}"
            e.printStackTrace()
            return false
        } finally {
            // Clean up temporary clone directory
            cloneDir.deleteDir()
        }
    }

    /**
     * Clone a Git repository
     * 
     * @param repoUrl URL of the repository to clone
     * @param targetDir Directory to clone into
     * @return boolean indicating success or failure
     */
    private static boolean cloneRepository(String repoUrl, String targetDir) {
        def cloneCommand = ["git", "clone", "--depth", "1", repoUrl, targetDir]
        println "[DEBUG] Executing clone: ${cloneCommand.join(' ')}"
        
        def cloneProcess = cloneCommand.execute()
        def cloneOut = new StringBuffer()
        def cloneErr = new StringBuffer()
        cloneProcess.waitForProcessOutput(cloneOut, cloneErr)

        if (cloneProcess.exitValue() != 0) {
            println "[ERROR] Git clone failed:\n${cloneErr}"
            return false
        }
        
        return true
    }

    /**
     * Run GitLeaks scan on a directory
     * 
     * @param scanDir Directory to scan
     * @param configPath Path to GitLeaks config file
     * @param reportPath Path to output report
     * @param verbose Enable verbose output
     * @return boolean indicating success or failure
     */
    private static boolean runGitLeaksScan(String scanDir, String configPath, String reportPath, boolean verbose) {
        // Prepare GitLeaks command
        def command = [
            "gitleaks", "detect",
            "--path=${scanDir}",
            "--report=${reportPath}",
            "--format=json",
            "--no-git",
            "--debug"
        ]
        
        // Add optional parameters
        if (verbose) {
            command << "--verbose"
        }
        if (configPath && new File(configPath).exists()) {
            command << "--config-path=${configPath}"
        }

        println "[DEBUG] Executing: ${command.join(' ')}"

        // Execute GitLeaks scan
        def process = command.execute()
        def stdout = new StringBuffer()
        def stderr = new StringBuffer()
        process.waitForProcessOutput(stdout, stderr)

        println "[OUTPUT]\n${stdout}"
        if (stderr) println "[ERROR]\n${stderr}"

        // Validate scan results
        return validateScanResults(reportPath)
    }

    /**
     * Validate scan results and determine if any leaks were found
     * 
     * @param reportPath Path to the report file
     * @return boolean True if no leaks found, false otherwise
     */
    private static boolean validateScanResults(String reportPath) {
        def reportFile = new File(reportPath)
        if (!reportFile.exists()) {
            println "[ERROR] Report file was not generated"
            return false
        }

        def reportContent = reportFile.text.trim()
        if (reportContent.startsWith("[") && reportContent.length() > 2) {
            println "[WARNING] GitLeaks scan found potential security issues. Check report at ${reportPath}."
            return false
        } else {
            println "[SUCCESS] GitLeaks scan completed with no leaks."
            return true
        }
    }
}
