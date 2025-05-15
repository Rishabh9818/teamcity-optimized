import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script

object GitLeaksScanPipeline : BuildType({
    name = "GitLeaks Security Scan"

    // VCS settings
    vcs {
        root(DslContext.settingsRoot)
    }

    // Define parameters that can be configured in TeamCity
    params {
        // Repository URL to scan (required)
        param("teamcity.gitLeaks.repoUrl", "") {
            description = "URL of the Git repository to scan"
            allowEmpty = false
            display = ParameterDisplay.PROMPT
        }
        
        // Credentials for repository access (optional)
        param("teamcity.gitLeaks.credentialsId", "") {
            description = "Credentials ID for repository access (if needed)"
            allowEmpty = true
        }
        
        // Branch to scan (optional, defaults to main)
        param("teamcity.gitLeaks.branchName", "main") {
            description = "Branch name to scan"
            allowEmpty = false
        }
        
        // Configuration file path
        param("teamcity.gitLeaks.configPath", "%teamcity.build.checkoutDir%/ci-teamcity-pipeline/gitleaks.toml") {
            description = "Path to the GitLeaks configuration file"
            allowEmpty = false
        }
        
        // Report output path
        param("teamcity.gitLeaks.reportPath", "%teamcity.build.checkoutDir%/gitleaks-report.json") {
            description = "Path where the scan report will be saved"
            allowEmpty = false
        }
        
        // Verbose logging flag
        param("teamcity.gitLeaks.verbose", "false") {
            description = "Enable verbose logging"
            allowEmpty = false
            options {
                option("true", "Yes")
                option("false", "No")
            }
        }
    }

    // Build steps
    steps {
        script {
            name = "Run GitLeaks Security Scan"
            scriptContent = """
                #!/bin/bash
                
                # TeamCity Script for GitLeaks Security Scanning
                # This script prepares the environment and calls the GitLeaks wrapper
                
                # Navigate to the project directory
                cd %teamcity.build.checkoutDir%
                
                # Print environment and configuration info
                echo "===================================================="
                echo "GitLeaks Security Scan - TeamCity Script"
                echo "===================================================="
                echo "Build checkout directory: %teamcity.build.checkoutDir%"
                echo "Repository URL: %teamcity.gitLeaks.repoUrl%"
                echo "Credentials ID: %teamcity.gitLeaks.credentialsId%"
                echo "Branch Name: %teamcity.gitLeaks.branchName%"
                echo "Config Path: %teamcity.gitLeaks.configPath%"
                echo "Report Path: %teamcity.gitLeaks.reportPath%"
                echo "Verbose Mode: %teamcity.gitLeaks.verbose%"
                echo "===================================================="
                
                # Verify required tools are available
                which git || { echo "ERROR: Git is not installed"; exit 1; }
                which groovy || { echo "ERROR: Groovy is not installed"; exit 1; }
                which gitleaks || { echo "ERROR: GitLeaks is not installed"; exit 1; }
                
                # Validate required parameters
                if [ -z "%teamcity.gitLeaks.repoUrl%" ]; then
                    echo "##teamcity[buildProblem description='Error: Repository URL is required']"
                    exit 1
                fi
                
                # Set classpath for Groovy script execution
                GROOVY_CLASSPATH="%teamcity.build.checkoutDir%/ci-teamcity-pipeline/buildSrc"
                
                # Navigate to wrapper directory
                cd %teamcity.build.checkoutDir%/ci-teamcity-pipeline/wrapper/
                
                # Check if wrapper script exists
                if [ ! -f "gitleaksWrapper.groovy" ]; then
                    echo "##teamcity[buildProblem description='Error: gitleaksWrapper.groovy script not found']"
                    echo "Current directory: $(pwd)"
                    ls -la
                    exit 1
                fi
                
                # Construct verbose flag if needed
                VERBOSE_FLAG=""
                if [ "%teamcity.gitLeaks.verbose%" = "true" ]; then
                    VERBOSE_FLAG="--verbose"
                fi
                
                # Run the wrapper script with proper classpath
                echo "Executing GitLeaks wrapper script..."
                groovy -cp "$GROOVY_CLASSPATH" gitleaksWrapper.groovy \
                    --repo-url="%teamcity.gitLeaks.repoUrl%" \
                    --credentials-id="%teamcity.gitLeaks.credentialsId%" \
                    --branch-name="%teamcity.gitLeaks.branchName%" \
                    --config-path="%teamcity.gitLeaks.configPath%" \
                    --report-path="%teamcity.gitLeaks.reportPath%" \
                    $VERBOSE_FLAG
                
                # Store scan result
                SCAN_RESULT=$?
                
                # Display report if it exists
                if [ -f "%teamcity.gitLeaks.reportPath%" ]; then
                    echo "===================================================="
                    echo "GitLeaks Scan Report Summary:"
                    echo "===================================================="
                    
                    # Check if report is empty
                    if [ -s "%teamcity.gitLeaks.reportPath%" ]; then
                        # Try to count findings if JSON format
                        if grep -q "^[" "%teamcity.gitLeaks.reportPath%"; then
                            FINDINGS=$(grep -o "\"Description\"" "%teamcity.gitLeaks.reportPath%" | wc -l)
                            echo "Found $FINDINGS potential security issues."
                            
                            # Show first few findings
                            echo "First few findings (if any):"
                            head -50 "%teamcity.gitLeaks.reportPath%"
                            
                            if [ $FINDINGS -gt 5 ]; then
                                echo "... (see full report for all findings)"
                            fi
                        else
                            # If not valid JSON, just show the content
                            cat "%teamcity.gitLeaks.reportPath%"
                        fi
                    else
                        echo "No security issues found! Report is empty."
                    fi
                    
                    # Publish report as TeamCity artifact
                    echo "##teamcity[publishArtifacts '%teamcity.gitLeaks.reportPath% => security-reports/']"
                else
                    echo "WARNING: Report file was not generated at %teamcity.gitLeaks.reportPath%"
                fi
                
                # Exit with the same status as the scan
                echo "GitLeaks scan completed with status: $SCAN_RESULT"
                exit $SCAN_RESULT
            """.trimIndent()
        }
    }

    // Failure conditions based on scan results
    failureConditions {
        // Custom failure condition based on GitLeaks report content
        nonZeroExitCode = true
        
        // Add failure condition if sensitive info is found
        contains {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "GitLeaks scan found potential security issues"
            failureMessage = "Security scan detected potential secrets/credentials in code"
            reverse = false
            stopBuildOnFailure = true
        }
    }

    // Publish the gitleaks report as an artifact
    artifactRules = "%teamcity.gitLeaks.reportPath% => security-reports"
    
    // Only run if needed - don't run on every commit
    requirements {
        exists("env.TEAMCITY_RUN_GITLEAKS_SCAN", "true")
    }
    
    // Add clean workspace option
    options {
        cleanBuild = true
    }
})
