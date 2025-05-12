object GitLeaksScanPipeline : BuildType({
    name = "GitLeaks Security Scan"

    // VCS settings
    vcs {
        root(DslContext.settingsRoot)
    }

    // Define parameters that can be configured in TeamCity
    params {
        // Repository URL to scan - required
        param("teamcity.gitLeaks.repoUrl", "") {
            label = "Git Repository URL"
            description = "URL of the Git repository to scan"
            display = ParameterDisplay.PROMPT
        }
        
        // Optional configuration file path (can be left empty)
        param("teamcity.gitLeaks.configPath", "%teamcity.build.checkoutDir%/ci-teamcity-pipeline/gitleaks.toml") {
            label = "GitLeaks Config Path"
            description = "Path to custom GitLeaks configuration file (leave empty to use default)"
            display = ParameterDisplay.NORMAL
        }
        
        // Report output path
        param("teamcity.gitLeaks.reportPath", "%teamcity.build.checkoutDir%/gitleaks-report.json") {
            label = "Report Output Path"
            description = "Path where the GitLeaks report will be saved"
            display = ParameterDisplay.NORMAL
        }
        
        // Verbose logging flag
        param("teamcity.gitLeaks.verbose", "false") {
            label = "Enable Verbose Logging"
            description = "Enable detailed logging during the scan"
            display = ParameterDisplay.CHECKBOX
            allowEmpty = false
        }
    }

    // Build steps
    steps {
        script {
            name = "Run GitLeaks Security Scan"
            scriptContent = """
                #!/bin/bash
                set -e

                # Set up Groovy environment
                GROOVY_HOME=/home/buildagent/buildAgent/groovy-4.0.12
                export PATH=$GROOVY_HOME/bin:$PATH
                groovy --version

                # Print debug information
                echo "Starting GitLeaks Security Scan"
                echo "Repository URL: %teamcity.gitLeaks.repoUrl%"
                echo "Config Path: %teamcity.gitLeaks.configPath%"
                echo "Report Path: %teamcity.gitLeaks.reportPath%"
                echo "Verbose Mode: %teamcity.gitLeaks.verbose%"

                # Navigate to the project directory
                cd %teamcity.build.checkoutDir%/ci-teamcity-pipeline/

                # Ensure directory structure exists
                mkdir -p buildSrc/security
                mkdir -p wrapper

                # Verify files are in the correct places
                if [ ! -f "buildSrc/security/GitLeaksScanner.groovy" ]; then
                    echo "ERROR: GitLeaksScanner.groovy not found at expected location"
                    exit 1
                fi

                if [ ! -f "wrapper/gitleaksWrapper.groovy" ]; then
                    echo "ERROR: gitleaksWrapper.groovy not found at expected location"
                    exit 1
                fi

                # Prepare verbose flag
                VERBOSE_FLAG=""
                if [ "%teamcity.gitLeaks.verbose%" == "true" ]; then
                    VERBOSE_FLAG="--verbose"
                fi

                # Validate repository URL
                if [ -z "%teamcity.gitLeaks.repoUrl%" ]; then
                    echo "Error: Repository URL is required"
                    exit 1
                fi

                # Prepare config path (use empty string if null)
                CONFIG_PATH="%teamcity.gitLeaks.configPath%"
                if [ -z "$CONFIG_PATH" ] || [ "$CONFIG_PATH" == " " ]; then
                    CONFIG_PATH="null"
                fi

                # Execute the wrapper script from its directory
                cd wrapper/
                echo "Running GitLeaks scan wrapper..."

                # Ensure script is executable
                chmod +x gitleaksWrapper.groovy

                # Run the wrapper script with parameters from TeamCity
                groovy gitleaksWrapper.groovy \\
                    "%teamcity.gitLeaks.repoUrl%" \\
                    "$CONFIG_PATH" \\
                    "%teamcity.gitLeaks.reportPath%" \\
                    $VERBOSE_FLAG

                # Store scan result
                SCAN_RESULT=$?

                # Display report if it exists
                if [ -f "%teamcity.gitLeaks.reportPath%" ]; then
                    echo "GitLeaks Scan Report:"
                    cat "%teamcity.gitLeaks.reportPath%"
                fi

                # Exit with scan result
                exit $SCAN_RESULT
            """.trimIndent()
        }
    }

    // Publish the gitleaks report as an artifact
    artifactRules = "gitleaks-report.json => security-reports"
    
    // Failure conditions
    failureConditions {
        // Consider build as failed if the gitleaks scan fails
        executionTimeoutMin = 10
    }
})
