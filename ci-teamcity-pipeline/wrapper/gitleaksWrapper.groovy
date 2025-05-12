#!/usr/bin/env groovy

// Import the GitLeaksScanner class
@Grab('ci-teamcity-pipeline:buildSrc')
import security.GitLeaksScanner

// Main script execution
class GitLeaksWrapper {
    static void main(String[] args) {
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

        // Perform the scan
        def result = GitLeaksScanner.scan(gitUrl, configPath, reportPath, verboseFlag)
        
        // Exit with appropriate status code
        System.exit(result ? 0 : 1)
    }
}

// Invoke the main method
GitLeaksWrapper.main(this.args)