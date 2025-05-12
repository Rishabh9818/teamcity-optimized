#!/usr/bin/env groovy

/**
 * Main wrapper script for GitLeaks scanning
 * This wrapper script is designed to be called from TeamCity with parameters
 */

// Find GitLeaksScanner.groovy file
def findScannerFile() {
    def scriptDir = new File(getClass().protectionDomain.codeSource.location.path).parent
    def possiblePaths = [
        "${scriptDir}/../buildSrc/security/GitLeaksScanner.groovy",
        "${scriptDir}/../buildSrc/sharedlib/security/GitLeaksScanner.groovy"
    ]
    
    // Try to add project root paths if teamcity.build.checkoutDir is defined
    def projectRoot = System.getProperty("teamcity.build.checkoutDir", null)
    if (projectRoot) {
        possiblePaths.add("${projectRoot}/ci-teamcity-pipeline/buildSrc/security/GitLeaksScanner.groovy")
        possiblePaths.add("${projectRoot}/ci-teamcity-pipeline/buildSrc/sharedlib/security/GitLeaksScanner.groovy")
    }
    
    // Try each path
    for (path in possiblePaths) {
        def file = new File(path)
        if (file.exists()) {
            println "[INFO] Found scanner at: ${file.absolutePath}"
            return file
        }
    }
    
    throw new FileNotFoundException("Cannot find GitLeaksScanner.groovy file. Searched in: ${possiblePaths.join(', ')}")
}

/**
 * Compiles and loads security.GitLeaksScanner class
 */
def loadGitLeaksScanner() {
    def file = findScannerFile()
    
    // Create a separate class loader that includes the parent directory as well
    def parentDir = file.parentFile.parentFile.parentFile  // Navigate up to buildSrc or sharedlib
    def urls = [parentDir.toURI().toURL()] as URL[]
    def classLoader = new URLClassLoader(urls, this.class.classLoader)
    
    // Load the source code
    def sourceCode = file.text
    
    // Compile the class
    def config = new CompilerConfiguration()
    def shell = new GroovyShell(classLoader, new Binding(), config)
    shell.evaluate(sourceCode)
    
    // Now try to load the class
    try {
        def scannerClass = classLoader.loadClass("security.GitLeaksScanner")
        return scannerClass
    } catch (ClassNotFoundException e) {
        println "[ERROR] Failed to load security.GitLeaksScanner: ${e.message}"
        
        // Try alternative approach using reflection
        println "[INFO] Attempting alternative class loading approach..."
        for (Class<?> c : shell.getClassLoader().getLoadedClasses()) {
            if (c.getName().endsWith("GitLeaksScanner")) {
                println "[INFO] Found class: ${c.getName()}"
                return c
            }
        }
        
        throw new ClassNotFoundException("Could not load GitLeaksScanner class")
    }
}

/**
 * Call GitLeaksScanner scan method with provided parameters
 * 
 * @param repoUrl URL of the repository to scan
 * @param configPath Path to the GitLeaks configuration file (optional)
 * @param reportPath Path where the scan report will be generated
 * @param verbose Enable verbose logging
 * @return boolean indicating scan success or failure
 */
def scan(String repoUrl, String configPath, String reportPath, boolean verbose = false) {
    def scannerClass = loadGitLeaksScanner()
    
    println "[INFO] Starting GitLeaks scan with parameters:"
    println "  Repository URL: ${repoUrl}"
    println "  Config Path: ${configPath ?: 'null'}"
    println "  Report Path: ${reportPath}"
    println "  Verbose Mode: ${verbose}"
    
    // Invoke the static scan method from the loaded class
    return scannerClass.scan(repoUrl, configPath, reportPath, verbose)
}

// When executed directly (not imported)
if (this.getClass().getName() == 'gitleaksWrapper') {
    // Validate input arguments
    if (args.length < 2) {
        println "Usage: groovy gitleaksWrapper.groovy <git-url> <report-path> [config-path] [--verbose]"
        System.exit(1)
        return
    }

    // Parse arguments
    def gitUrl = args[0]
    def reportPath = args[1]
    def configPath = (args.length > 2 && args[2] != "null") ? args[2] : null
    def verboseFlag = (args.contains("--verbose"))

    // Call the scan method
    def result = scan(gitUrl, configPath, reportPath, verboseFlag)
    
    // Exit with appropriate status code
    System.exit(result ? 0 : 1)
}
