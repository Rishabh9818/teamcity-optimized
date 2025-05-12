#!/usr/bin/env groovy

/**
 * Main wrapper script for GitLeaks scanning
 * This wrapper script is designed to be called from TeamCity with parameters
 */

// Load the GitLeaksScanner class
def loadGitLeaksScanner() {
    // First try to find the file relative to current script
    def scriptDir = new File(getClass().protectionDomain.codeSource.location.path).parent
    def scannerPath = new File("${scriptDir}/../buildSrc/sharedlib/security/GitLeaksScanner.groovy")
    
    if (!scannerPath.exists()) {
        // Try with absolute path from project root
        def projectRoot = new File(System.getProperty("teamcity.build.checkoutDir", "."))
        scannerPath = new File("${projectRoot}/ci-teamcity-pipeline/buildSrc/sharedlib/security/GitLeaksScanner.groovy")
        
        if (!scannerPath.exists()) {
            throw new FileNotFoundException("Cannot find GitLeaksScanner.groovy file. Looked in: ${scannerPath.absolutePath}")
        }
    }
    
    println "[INFO] Loading scanner from: ${scannerPath.absolutePath}"
    return new GroovyShell().parse(scannerPath)
}

/**
 * Call GitLeaksScanner scan method with provided parameters
 * 
 * @param repoUrl URL of the repository to scan
 * @param configPath Path to the GitLeaks configuration file (optional)
 * @param reportPath Path where the scan report will be generated
 * @param verbose Enable verbose logging
 * @return boolean indicating scan success or failure
 */
def scan(String repoUrl, String configPath, String reportPath, boolean verbose = false) {
    def scanner = loadGitLeaksScanner()
    
    println "[INFO] Starting GitLeaks scan with parameters:"
    println "  Repository URL: ${repoUrl}"
    println "  Config Path: ${configPath ?: 'null'}"
    println "  Report Path: ${reportPath}"
    println "  Verbose Mode: ${verbose}"
    
    // Invoke the scan method from the loaded class
    return scanner.scan(repoUrl, configPath, reportPath, verbose)
}

// When executed directly (not imported)
if (this.getClass().getName() == 'gitleaksWrapper') {
    // Validate input arguments
    if (args.length < 2) {
        println "Usage: groovy gitleaksWrapper.groovy <git-url> <report-path> [config-path] [--verbose]"
        System.exit(1)
        return
    }

    // Parse arguments
    def gitUrl = args[0]
    def reportPath = args[1]
    def configPath = (args.length > 2 && args[2] != "null") ? args[2] : null
    def verboseFlag = (args.contains("--verbose"))

    // Call the scan method
    def result = scan(gitUrl, configPath, reportPath, verboseFlag)
    
    // Exit with appropriate status code
    System.exit(result ? 0 : 1)
}
