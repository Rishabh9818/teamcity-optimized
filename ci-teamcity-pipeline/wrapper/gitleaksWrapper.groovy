#!/usr/bin/env groovy

/**
 * GitLeaks wrapper for TeamCity integration
 * Implementing direct functionality without complex class loading
 */

// Define the base package structure
class GitLeaksWrapper {
    /**
     * Main entry point that will be called from TeamCity
     * Following the pattern similar to Jenkins Shared Library
     */
    def call(Map params) {
        // Default parameters
        def repoUrl = params.repoUrl ?: ""
        def configPath = params.configPath ?: null
        def reportPath = params.reportPath ?: "./gitleaks-report.json"
        def verbose = params.verbose ?: false
        
        println "[INFO] Starting GitLeaks scan with parameters:"
        println "  Repository URL: ${repoUrl}"
        println "  Config Path: ${configPath ?: 'null'}"
        println "  Report Path: ${reportPath}"
        println "  Verbose Mode: ${verbose}"
        
        // Directly execute the scan logic
        return scan(repoUrl, configPath, reportPath, verbose)
    }
    
    /**
     * Perform GitLeaks scan directly
     * This implements the functionality from GitLeaksScanner without class loading
     */
    boolean scan(String repoUrl, String configPath, String reportPath, boolean verbose = false) {
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
            // Clean up temporary directory
            cloneDir.deleteDir()
        }
    }

    /**
     * Clone a Git repository
     */
    private boolean cloneRepository(String repoUrl, String targetDir) {
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
            return false
        } else {
            println "[SUCCESS] GitLeaks scan completed with no leaks."
            return true
        }
    }
}

// When executed directly from command line
def wrapper = new GitLeaksWrapper()

if (this.getClass().getName() == 'gitleaksWrapper') {
    // Parse command line arguments
    if (args.length < 2) {
        println "Usage: groovy gitleaksWrapper.groovy <git-url> <report-path> [config-path] [--verbose]"
        System.exit(1)
        return
    }

    def params = [
        repoUrl: args[0],
        reportPath: args[1],
        configPath: (args.length > 2 && args[2] != "null") ? args[2] : null,
        verbose: args.contains("--verbose")
    ]

    // Call main method
    def result = wrapper.call(params)
    
    // Exit with appropriate status code
    System.exit(result ? 0 : 1)
}
