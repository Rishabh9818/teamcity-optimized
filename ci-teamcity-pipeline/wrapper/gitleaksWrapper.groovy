#!/usr/bin/env groovy

// Import the scanner class
// Note: We're using a direct path reference instead of package import for simplicity
def scannerClass = new GroovyClassLoader().parseClass(new File("..../buildSrc/sharedlib/security/GitLeaksScanner.groovy"))

/**
 * Main wrapper script for GitLeaks scanning
 */
class GitLeaksWrapper {
    static void main(String[] args) {
        // Get reference to scanner class
        def scanner = Class.forName("security.GitLeaksScanner")
        
        // Validate input arguments
        if (args.length < 3 || args.length > 4) {
            println "Usage: groovy gitleaksWrapper.groovy <git-url> <config-path> <report-path> [--verbose]"
            System.exit(1)
            return
        }

        // Parse arguments
        def gitUrl = args[0]
        def configPath = args[1] != "null" ? args[1] : null
        def reportPath = args[2]
        def verboseFlag = (args.length == 4 && args[3] == "--verbose")

        println "[INFO] Starting GitLeaks scan with parameters:"
        println "  Repository URL: ${gitUrl}"
        println "  Config Path: ${configPath}"
        println "  Report Path: ${reportPath}"
        println "  Verbose Mode: ${verboseFlag}"

        // Call the scanner's scan method
        def result = scanner.scan(gitUrl, configPath, reportPath, verboseFlag)
        
        // Exit with appropriate status code
        System.exit(result ? 0 : 1)
    }
}

// Execute the wrapper
GitLeaksWrapper.main(this.args)
