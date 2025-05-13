#!/usr/bin/env groovy
// Import required class
import sharedlib.security.GitLeaksScanner

/**
 * GitLeaks wrapper for TeamCity integration
 * This script serves as an entry point for TeamCity to invoke GitLeaks scanning
 */

/**
 * Helper function to get TeamCity parameters/environment variables
 * 
 * @param paramName Name of the parameter to retrieve
 * @param defaultValue Default value if parameter is not found
 * @return The parameter value or default if not found
 */
def getTeamCityParam(String paramName, String defaultValue = '') {
    return System.getenv(paramName) ?: defaultValue
}

/**
 * Main method to execute the GitLeaks scan
 */
def call() {
    println "[Wrapper] Starting GitLeaks security scan..."
    
    // Initialize the GitLeaksScanner
    def scanner = new GitLeaksScanner()
    
    // Define parameters from TeamCity configuration parameters
    def scanParams = [
        repoUrl    : getTeamCityParam('teamcity.gitLeaks.repoUrl', ''),
        configPath : getTeamCityParam('teamcity.gitLeaks.configPath', './ci-teamcity-pipeline/gitleaks.toml'),
        reportPath : getTeamCityParam('teamcity.gitLeaks.reportPath', './gitleaks-report.json'),
        verbose    : getTeamCityParam('teamcity.gitLeaks.verbose', 'false').toBoolean()
    ]
    
    try {
        println "[Wrapper] GitLeaks scan parameters: ${scanParams}"
        
        // Call the scanner with parameters
        def result = scanner.scan(scanParams)
        
        if (result) {
            println "##teamcity[buildStatus status='SUCCESS' text='GitLeaks scan completed with no leaks']"
            System.exit(0)
        } else {
            println "##teamcity[buildStatus status='FAILURE' text='GitLeaks scan found security issues or failed']"
            System.exit(1)
        }
        
    } catch (Exception e) {
        // Proper TeamCity message escaping
        def escapedMessage = e.message.replace("'", "|'").replace("\n", "|n")
        println "##teamcity[buildProblem description='GitLeaks scan failed: ${escapedMessage}']"
        println "##teamcity[buildStatus status='FAILURE' text='GitLeaks scan failed']"
        println "[ERROR] ${e.message}"
        e.printStackTrace()
        System.exit(1)
    }
}

// When executed directly from command line
if (this.getClass().getName() == 'gitleaksWrapper') {
    // Parse command line arguments
    if (args.length > 0 && args[0] == '--help') {
        println "Usage: groovy gitleaksWrapper.groovy [--repo-url=URL] [--config-path=PATH] [--report-path=PATH] [--verbose]"
        println "Note: Parameters can also be provided via environment variables"
        System.exit(0)
        return
    }
    
    // Set environment variables based on command line arguments
    args.each { arg ->
        if (arg.startsWith('--repo-url=')) {
            System.setProperty('teamcity.gitLeaks.repoUrl', arg.substring('--repo-url='.length()))
        }
        else if (arg.startsWith('--config-path=')) {
            System.setProperty('teamcity.gitLeaks.configPath', arg.substring('--config-path='.length()))
        }
        else if (arg.startsWith('--report-path=')) {
            System.setProperty('teamcity.gitLeaks.reportPath', arg.substring('--report-path='.length()))
        }
        else if (arg == '--verbose') {
            System.setProperty('teamcity.gitLeaks.verbose', 'true')
        }
    }
    
    // Call main method
    call()
}
