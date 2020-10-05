def dummyTestToBeRemovedCoFromBranchCvs(patchConfig) {
    patchConfig.services.each {
        println("Service = ${it.serviceName}")
        println("CVS Branch = ${it.microServiceBranch}")
        println("List of modules mavenArtifacs:")
        it.mavenArtifacts.each {
            println("   Artifact name = ${it.name}")
        }
    }

    println("List of DB Modules:")
    patchConfig.dbObjects.each{
            println("   ModuleName = ${it.moduleName}")
    }
}

def patchBuildsConcurrent(patchConfig) {
    node {
        deleteDir()
        lock("${patchConfig.serviceName}${patchConfig.currentTarget}Build") {
            coPackagerProjectsFromBranchCvs(patchConfig)

            /*
            nextRevision(patchConfig)
            generateVersionProperties(patchConfig)
            buildAndReleaseModulesConcurrent(patchConfig)
            saveRevisions(patchConfig)

             */
        }
    }
}

def coPackagerProjectsFromBranchCvs(patchConfig) {
    patchConfig.services.each {
        // JHE (05.10.2020) : by convention, the corresponding packager name is : <service-name>-pkg
        //def servicePackagerName = "${it.serviceName}-pkg"
        // TODO JHE (05.10.2020): remove hardcoded value
        def servicePackagerName = "testapp-pkg"
        coFromBranchCvs(patchConfig,it.microServiceBranch,servicePackagerName)
    }
}

def coFromBranchCvs(patchConfig, cvsBranch, moduleName) {
    def callBack = benchmark()
    def duration = callBack {
        checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
                [compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [
                        [location: [$class: 'BranchRepositoryLocation', branchName: cvsBranch, useHeadIfNotFound: false],  modules: [
                                [localName: moduleName, remoteName: moduleName]
                        ]]
                ]]
        ], skipChangeLog: false])
    }
    log("Checkout of ${moduleName} took ${duration} ms","coFromBranchCvs")
}

// Used in order to have Datetime info in our pipelines
def log(msg,caller) {
    def dt = "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}"
    def logMsg = caller != null ? "(${caller}) ${dt}: ${msg}" : "${dt}: ${msg}"
    echo logMsg
}

// Used in order to have Datetime info in our pipelines
def log(msg) {
    log(msg,null)
}

def benchmark() {
    def benchmarkCallback = { closure ->
        start = System.currentTimeMillis()
        closure.call()
        now = System.currentTimeMillis()
        now - start
    }
    benchmarkCallback
}