#!/usr/bin/env groovy

// Import required class
import sharedlib.security.GitLeaksScanner

/**
 * GitLeaks wrapper for TeamCity integration
 * This script serves as an entry point for TeamCity to invoke GitLeaks scanning
 */
def call() {
    println "[Wrapper] Starting GitLeaks security scan..."
    
    // Get TeamCity parameters/environment variables
    def getTeamCityParam(String paramName, String defaultValue = '') {
        return System.getenv(paramName) ?: defaultValue
    }
    
    // Initialize the GitLeaksScanner
    def scanner = new GitLeaksScanner()
    
    // Define parameters from TeamCity configuration parameters
    def scanParams = [
        repoUrl    : getTeamCityParam('gitleaks_repo_url', ''),
        configPath : getTeamCityParam('gitleaks_config_path', './ci-teamcity-pipeline/gitleaks.toml'),
        reportPath : getTeamCityParam('gitleaks_report_path', './gitleaks-report.json'),
        verbose    : getTeamCityParam('gitleaks_verbose', 'false').toBoolean()
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
    if (args.length == 0) {
        println "Usage: groovy gitleaksWrapper.groovy [--repo-url=URL] [--config-path=PATH] [--report-path=PATH] [--verbose]"
        println "Note: Parameters can also be provided via environment variables"
        System.exit(1)
        return
    }
    
    // Set environment variables based on command line arguments
    args.each { arg ->
        if (arg.startsWith('--repo-url=')) {
            System.setProperty('gitleaks_repo_url', arg.substring('--repo-url='.length()))
        }
        else if (arg.startsWith('--config-path=')) {
            System.setProperty('gitleaks_config_path', arg.substring('--config-path='.length()))
        }
        else if (arg.startsWith('--report-path=')) {
            System.setProperty('gitleaks_report_path', arg.substring('--report-path='.length()))
        }
        else if (arg == '--verbose') {
            System.setProperty('gitleaks_verbose', 'true')
        }
    }
    
    // Call main method
    call()
}
