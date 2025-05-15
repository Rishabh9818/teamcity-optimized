#!/usr/bin/env groovy
// Import required class
// import sharedlib.security.GitLeaksScanner

#!/usr/bin/env groovy
import sharedlib.security.GitLeaksScanner

/**
 * GitLeaks wrapper for TeamCity integration
 * This script serves as an entry point for TeamCity to invoke GitLeaks scanning
 */
class GitLeaksWrapper {
    /**
     * Get a TeamCity parameter with fallback to default value
     */
    static String getTeamCityParam(String paramName, String defaultValue = '') {
        return System.getenv(paramName) ?: defaultValue
    }

    /**
     * Main method to execute the GitLeaks scan
     * 
     * @param repoUrl Repository URL to scan (optional, can be provided via TeamCity param)
     * @param credentialsId Credentials to access the repository (optional)
     * @param branchName Branch to scan (optional)
     * @return boolean indicating scan success or failure
     */
    static boolean call(String repoUrl = null, String credentialsId = null, String branchName = null) {
        println "[Wrapper] Starting GitLeaks security scan..."
        
        // Define parameters from TeamCity configuration parameters or method arguments
        def scanParams = [
            repoUrl      : repoUrl ?: getTeamCityParam('teamcity.gitLeaks.repoUrl', ''),
            credentialsId: credentialsId ?: getTeamCityParam('teamcity.gitLeaks.credentialsId', ''),
            branchName   : branchName ?: getTeamCityParam('teamcity.gitLeaks.branchName', 'main'),
            configPath   : getTeamCityParam('teamcity.gitLeaks.configPath', './ci-teamcity-pipeline/gitleaks.toml'),
            reportPath   : getTeamCityParam('teamcity.gitLeaks.reportPath', './gitleaks-report.json'),
            verbose      : getTeamCityParam('teamcity.gitLeaks.verbose', 'false').toBoolean()
        ]
        
        try {
            println "[Wrapper] GitLeaks scan parameters: ${scanParams}"
            
            // Call the GitLeaksScanner with parameters
            def result = GitLeaksScanner.scan(scanParams)
            
            if (result) {
                println "##teamcity[buildStatus status='SUCCESS' text='GitLeaks scan completed with no leaks']"
                return true
            } else {
                println "##teamcity[buildStatus status='FAILURE' text='GitLeaks scan found security issues or failed']"
                return false
            }
            
        } catch (Exception e) {
            // Proper TeamCity message escaping
            def escapedMessage = e.message.replace("'", "|'").replace("\n", "|n")
            println "##teamcity[buildProblem description='GitLeaks scan failed: ${escapedMessage}']"
            println "##teamcity[buildStatus status='FAILURE' text='GitLeaks scan failed']"
            println "[ERROR] ${e.message}"
            e.printStackTrace()
            return false
        }
    }
}

// When executed directly from command line
if (this.getClass().getName() == 'gitleaksWrapper') {
    // Parse command line arguments
    if (args.length > 0 && args[0] == '--help') {
        println """
Usage: groovy gitleaksWrapper.groovy [OPTIONS]
Options:
  --repo-url=URL       Repository URL to scan
  --credentials-id=ID  Credentials ID for repo access
  --branch-name=NAME   Branch name to scan (default: main)
  --config-path=PATH   Path to GitLeaks config file
  --report-path=PATH   Path for scan report output
  --verbose            Enable verbose logging
  
Note: Parameters can also be provided via environment variables
"""
        System.exit(0)
        return
    }
    
    // Extract command line arguments
    String argRepoUrl = null
    String argCredentialsId = null
    String argBranchName = null
    
    args.each { arg ->
        if (arg.startsWith('--repo-url=')) {
            argRepoUrl = arg.substring('--repo-url='.length())
        }
        else if (arg.startsWith('--credentials-id=')) {
            argCredentialsId = arg.substring('--credentials-id='.length())
        }
        else if (arg.startsWith('--branch-name=')) {
            argBranchName = arg.substring('--branch-name='.length())
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
    
    // Call main method with extracted arguments
    def result = new GitLeaksWrapper().call(argRepoUrl, argCredentialsId, argBranchName)
    System.exit(result ? 0 : 1)
}
