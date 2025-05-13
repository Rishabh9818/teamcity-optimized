#!/usr/bin/env groovy
// Import required class
import sharedlib.security.GitLeaksScanner

/**
 * GitLeaks wrapper for TeamCity integration
 * This script serves as an entry point for TeamCity to invoke GitLeaks scanning
 */

/**
 * Helper function to get TeamCity parameters/environment variables with extensive fallback options
 * 
 * @param paramName Name of the parameter to retrieve
 * @param defaultValue Default value if parameter is not found
 * @return The parameter value or default if not found
 */
def getTeamCityParam(String paramName, String defaultValue = '') {
    // Check system properties first (for command line args)
    def propValue = System.getProperty(paramName)
    if (propValue) return propValue
    
    // Check environment variables - try different formats
    def envValue = System.getenv(paramName)
    if (envValue) return envValue
    
    // Try with dots replaced by underscores (common TeamCity pattern)
    def altParamName = paramName.replace('.', '_')
    envValue = System.getenv(altParamName)
    if (envValue) return envValue
    
    // Try with all uppercase
    altParamName = altParamName.toUpperCase()
    envValue = System.getenv(altParamName)
    if (envValue) return envValue
    
    // Try special TeamCity format
    altParamName = "env.${paramName}"
    envValue = System.getenv(altParamName)
    if (envValue) return envValue
    
    return defaultValue
}

/**
 * Main method to execute the GitLeaks scan
 */
def call() {
    println "[Wrapper] Starting GitLeaks security scan..."
    
    // Print all available environment variables for debugging
    println "[Wrapper] Environment parameters relevant to GitLeaks:"
    System.getenv().each { k, v ->
        if (k.toLowerCase().contains("gitleaks") || k.toLowerCase().contains("teamcity")) {
            println "  ${k} = ${v}"
        }
    }
    
    // Try different parameter naming conventions used by TeamCity
    def repoUrl = getTeamCityParam('teamcity.gitLeaks.repoUrl', '')
    if (!repoUrl) repoUrl = getTeamCityParam('TEAMCITY_GITLEAKS_REPOURL', '')
    if (!repoUrl) repoUrl = getTeamCityParam('gitleaks_repo_url', '')
    if (!repoUrl) repoUrl = getTeamCityParam('GITLEAKS_REPO_URL', '')
    
    def configPath = getTeamCityParam('teamcity.gitLeaks.configPath', './ci-teamcity-pipeline/gitleaks.toml')
    if (configPath.isEmpty()) configPath = getTeamCityParam('TEAMCITY_GITLEAKS_CONFIGPATH', './ci-teamcity-pipeline/gitleaks.toml')
    if (configPath.isEmpty()) configPath = getTeamCityParam('gitleaks_config_path', './ci-teamcity-pipeline/gitleaks.toml')
    if (configPath.isEmpty()) configPath = getTeamCityParam('GITLEAKS_CONFIG_PATH', './ci-teamcity-pipeline/gitleaks.toml')
    
    def reportPath = getTeamCityParam('teamcity.gitLeaks.reportPath', './gitleaks-report.json')
    if (reportPath.isEmpty()) reportPath = getTeamCityParam('TEAMCITY_GITLEAKS_REPORTPATH', './gitleaks-report.json')
    if (reportPath.isEmpty()) reportPath = getTeamCityParam('gitleaks_report_path', './gitleaks-report.json')
    if (reportPath.isEmpty()) reportPath = getTeamCityParam('GITLEAKS_REPORT_PATH', './gitleaks-report.json')
    
    def verboseStr = getTeamCityParam('teamcity.gitLeaks.verbose', 'false')
    if (verboseStr.isEmpty()) verboseStr = getTeamCityParam('TEAMCITY_GITLEAKS_VERBOSE', 'false')
    if (verboseStr.isEmpty()) verboseStr = getTeamCityParam('gitleaks_verbose', 'false')
    if (verboseStr.isEmpty()) verboseStr = getTeamCityParam('GITLEAKS_VERBOSE', 'false')
    
    def verbose = verboseStr.toString().toLowerCase() == 'true'
    
    // Initialize the GitLeaksScanner
    def scanner = new GitLeaksScanner()
    
    // Define parameters from TeamCity configuration parameters
    def scanParams = [
        repoUrl    : repoUrl,
        configPath : configPath,
        reportPath : reportPath,
        verbose    : verbose
    ]
    
    try {
        println "[Wrapper] GitLeaks scan parameters: ${scanParams}"
        
        // Validate required parameters
        if (!repoUrl) {
            throw new Exception("Repository URL is required but was not provided")
        }
        
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
    // Log the environment for debugging
    println "Environment variables:"
    System.getenv().each { k, v ->
        if (!k.toLowerCase().contains("password") && !k.toLowerCase().contains("secret")) {
            println "  ${k} = ${v}"
        }
    }
    
    // Parse command line arguments
    if (args.length > 0 && args[0] == '--help') {
        println "Usage: groovy gitleaksWrapper.groovy [--repo-url=URL] [--config-path=PATH] [--report-path=PATH] [--verbose]"
        println "Note: Parameters can also be provided via environment variables"
        System.exit(0)
        return
    }
    
    // Set system properties based on command line arguments
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
