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
        // Repository URL to scan
        param("teamcity.gitLeaks.repoUrl", "")
        
        // Credentials for repository access (optional)
        param("teamcity.gitLeaks.credentialsId", "")
        
        // Branch to scan (optional, defaults to main)
        param("teamcity.gitLeaks.branchName", "main")
        
        // Configuration file path
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
                #!/bin/bash
                
                # Set up environment
                echo "Starting GitLeaks Security Scan"
                cd %teamcity.build.checkoutDir%/ci-teamcity-pipeline
                
                # Validate required parameters
                if [ -z "%teamcity.gitLeaks.repoUrl%" ]; then
                    echo "##teamcity[buildProblem description='Error: Repository URL is required']"
                    exit 1
                fi
                
                # Set classpath for Groovy script
                GROOVY_CLASSPATH="%teamcity.build.checkoutDir%/ci-teamcity-pipeline/buildSrc"
                
                # Navigate to wrapper directory
                cd %teamcity.build.checkoutDir%/ci-teamcity-pipeline/wrapper/
                
                # Run the wrapper script
                groovy -cp "$GROOVY_CLASSPATH" gitleaksWrapper.groovy \
                    --repo-url="%teamcity.gitLeaks.repoUrl%" \
                    --credentials-id="%teamcity.gitLeaks.credentialsId%" \
                    --branch-name="%teamcity.gitLeaks.branchName%" \
                    --config-path="%teamcity.gitLeaks.configPath%" \
                    --report-path="%teamcity.gitLeaks.reportPath%" \
                    $([ "%teamcity.gitLeaks.verbose%" == "true" ] && echo "--verbose")
                
                # Store the scan result
                SCAN_RESULT=$?
                
                # Display report if it exists
                if [ -f "%teamcity.gitLeaks.reportPath%" ]; then
                    echo "GitLeaks Scan Report:"
                    cat "%teamcity.gitLeaks.reportPath%"
                    
                    # Publish report as TeamCity artifact
                    echo "##teamcity[publishArtifacts '%teamcity.gitLeaks.reportPath%']"
                fi
                
                exit $SCAN_RESULT
            """.trimIndent()
        }
    }

    // Publish the gitleaks report as an artifact
    artifactRules = "%teamcity.gitLeaks.reportPath% => security-reports"
})
