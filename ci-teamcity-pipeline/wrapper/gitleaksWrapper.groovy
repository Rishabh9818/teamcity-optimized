#!/usr/bin/env groovy

/**
 * GitLeaks wrapper for TeamCity integration
 * Follows Jenkins shared library pattern
 */

// Import required classes
import groovy.lang.GroovyShell
import org.codehaus.groovy.control.CompilerConfiguration

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
    
    // Import the GitLeaksScanner class
    def scanner = loadScanner()
    
    // Invoke the scan method
    return scanner.scan(repoUrl, configPath, reportPath, verbose)
}

/**
 * Locate and load the GitLeaksScanner class
 */
def loadScanner() {
    def projectRoot = System.getProperty("teamcity.build.checkoutDir", ".")
    def scannerPath = "${projectRoot}/ci-teamcity-pipeline/buildSrc/sharedlib/security/GitLeaksScanner.groovy"
    def scannerFile = new File(scannerPath)
    
    if (!scannerFile.exists()) {
        throw new FileNotFoundException("GitLeaksScanner.groovy not found at: ${scannerPath}")
    }
    
    println "[INFO] Found scanner at: ${scannerFile.absolutePath}"
    
    // Load the GitLeaksScanner class directly
    Class.forName("security.GitLeaksScanner")
    
    // Return the class as an object that can be used
    return new security.GitLeaksScanner()
}

// When executed directly from command line
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
    def result = call(params)
    
    // Exit with appropriate status code
    System.exit(result ? 0 : 1)
}
