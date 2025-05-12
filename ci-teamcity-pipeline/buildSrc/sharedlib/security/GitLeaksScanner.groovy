package security

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
            def cloneCommand = ["git", "clone", "--depth", "1", repoUrl, cloneDir.absolutePath]
            def cloneProcess = cloneCommand.execute()
            def cloneOut = new StringBuffer()
            def cloneErr = new StringBuffer()
            cloneProcess.waitForProcessOutput(cloneOut, cloneErr)

            if (cloneProcess.exitValue() != 0) {
                println "[ERROR] Git clone failed:\n${cloneErr}"
                return false
            }

            println "[INFO] Clone successful. Starting GitLeaks scan..."

            // Prepare GitLeaks command
            def command = [
                "gitleaks", "detect",
                "--path=${cloneDir.absolutePath}",
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
        } catch (Exception e) {
            println "[ERROR] Exception occurred during scan: ${e.message}"
            e.printStackTrace()
            return false
        } finally {
            // Clean up temporary clone directory
            cloneDir.deleteDir()
        }
    }
}