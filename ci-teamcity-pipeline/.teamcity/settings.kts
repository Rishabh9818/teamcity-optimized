object GitLeaksScanPipeline : BuildType({
    name = "GitLeaks Security Scan"

    // VCS settings
    vcs {
        root(DslContext.settingsRoot)
    }

    // Define parameters that can be configured in TeamCity
    params {
        // Repository URL to scan
        param("teamcity.gitLeaks.repoUrl", "")
        
        // Optional configuration file path (can be left empty)
        param("teamcity.gitLeaks.configPath", "%teamcity.build.checkoutDir%/ci-teamcity-pipeline/gitleaks.toml")
        
        // Report output path
        param("teamcity.gitLeaks.reportPath", "%teamcity.build.checkoutDir%/gitleaks-report.json")
        
        // Verbose logging flag
        param("teamcity.gitLeaks.verbose", "false")
    }

    // Build steps
    steps {
        script {
            name = "Run GitLeaks Security Scan"
            scriptContent = """
                echo "Starting GitLeaks Security Scan..."
                cd %teamcity.build.checkoutDir%/ci-teamcity-pipeline
                
                # Validate required parameters
                if [ -z "%teamcity.gitLeaks.repoUrl%" ]; then
                    echo "Error: Repository URL is required"
                    exit 1
                fi
                
                # Prepare verbose flag
                VERBOSE_FLAG=""
                if [ "%teamcity.gitLeaks.verbose%" == "true" ]; then
                    VERBOSE_FLAG="--verbose"
                fi
                
                # Run the gitleaksWrapper.groovy script
                groovy gitleaksWrapper.groovy \
                    "%teamcity.gitLeaks.repoUrl%" \
                    "%teamcity.gitLeaks.configPath%" \
                    "%teamcity.gitLeaks.reportPath%" \
                    $VERBOSE_FLAG
                
                # Check the result of the scan
                SCAN_RESULT=$?
                if [ $SCAN_RESULT -ne 0 ]; then
                    echo "GitLeaks scan failed. Check the report for details."
                    exit 1
                else
                    echo "GitLeaks scan completed successfully."
                fi
            """.trimIndent()
        }
    }

    // Publish the gitleaks report as an artifact
    artifactRules = "gitleaks-report.json => security-reports"
})