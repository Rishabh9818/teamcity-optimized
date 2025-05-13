package sharedlib.security

/**
 * GitLeaksScanner Class
 * Provides functionality for scanning repositories for credential leaks
 */
class GitLeaksScanner {
    /**
     * Main method to perform a GitLeaks security scan
     * 
     * @param params Map of parameters for the scan
     * @return boolean indicating scan success or failure
     */
    def scan(Map params) {
        // Extract parameters with defaults
        def repoUrl = params.repoUrl ?: ""
        def configPath = params.configPath ?: null
        def reportPath = params.reportPath ?: "./gitleaks-report.json"
        def verbose = params.verbose ?: false
        
        println "[INFO] Starting GitLeaks scan with parameters:"
        println "  Repository URL: ${repoUrl}"
        println "  Config Path: ${configPath ?: 'null'}"
        println "  Report Path: ${reportPath}"
        println "  Verbose Mode: ${verbose}"
        
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
            // Correct usage of the TeamCity service message
            println "##teamcity[buildProblem description='GitLeaks scan failed: ${e.message.replace("'", "|'")}']"
            e.printStackTrace()
            return false
        } finally {
            // Clean up temporary directory
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
    private boolean cloneRepository(String repoUrl, String targetDir) {
        def cloneCommand = ["git", "clone", "--depth", "1", repoUrl, targetDir]
        println "[DEBUG] Executing clone: ${cloneCommand.join(' ')}"
        
        def cloneProcess = new ProcessBuilder(cloneCommand)
            .redirectErrorStream(true)
            .start()
        
        // Capture and print output
        def cloneReader = new BufferedReader(new InputStreamReader(cloneProcess.getInputStream()))
        String cloneLine
        while ((cloneLine = cloneReader.readLine()) != null) {
            println(cloneLine)
        }
        
        def exitCode = cloneProcess.waitFor()
        if (exitCode != 0) {
            println "[ERROR] Git clone failed with exit code: ${exitCode}"
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
    private boolean runGitLeaksScan(String scanDir, String configPath, String reportPath, boolean verbose) {
        // Prepare GitLeaks command
        def command = [
            "gitleaks", "detect",
            "--path=${scanDir}",
            "--report=${reportPath}",
            "--format=json",
            "--no-git"
        ]
        
        // Add optional parameters
        if (verbose) {
            command << "--verbose"
            command << "--debug"
        }
        
        if (configPath && new File(configPath).exists()) {
            command << "--config-path=${configPath}"
        }

        println "[DEBUG] Executing: ${command.join(' ')}"

        // Execute GitLeaks scan
        def process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        
        // Capture and print output
        def reader = new BufferedReader(new InputStreamReader(process.getInputStream()))
        String line
        while ((line = reader.readLine()) != null) {
            println(line)
        }
        
        def exitCode = process.waitFor()
        if (exitCode != 0 && exitCode != 1) { // Exit code 1 could mean findings were detected
            println "[ERROR] GitLeaks scan failed with exit code: ${exitCode}"
            return false
        }

        // Validate scan results
        return validateScanResults(reportPath)
    }

    /**
     * Validate scan results and determine if any leaks were found
     * 
     * @param reportPath Path to the report file
     * @return boolean True if no leaks found, false otherwise
     */
    private boolean validateScanResults(String reportPath) {
        def reportFile = new File(reportPath)
        if (!reportFile.exists()) {
            println "[ERROR] Report file was not generated"
            return false
        }

        def reportContent = reportFile.text.trim()
        if (reportContent.startsWith("[") && reportContent.length() > 2) {
            println "[WARNING] GitLeaks scan found potential security issues. Check report at ${reportPath}."
            println "##teamcity[buildProblem description='Security issues detected by GitLeaks scan']"
            return false
        } else {
            println "[SUCCESS] GitLeaks scan completed with no leaks."
            return true
        }
    }
}
