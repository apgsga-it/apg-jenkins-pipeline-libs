def patchBuildsConcurrent(patchConfig) {
    node {
        patchConfig.services.each { service -> (
            lock("${service.serviceName}-${patchConfig.currentTarget}-Build") {
                deleteDir()
                // TODO JHE (05.10.2020) : service.packagerName needs to be implemented in Piper
                coFromBranchCvs(service.microServiceBranch,service.packagerName)

                publishNewRevisionFor(service)

                // TODO JHE (05.10.2020): to be checked, do we still need this step ??
                // generateVersionProperties(patchConfig)

                buildAndReleaseModulesConcurrent(patchConfig)

                /*
                saveRevisions(patchConfig)
                 */
            }
       )}
    }
}

def buildAndReleaseModulesConcurrent(patchConfig) {
    //TODO JHE (05.10.2020) : do we want to parallelize services as well ?
    patchConfig.services.each { service ->
        // TODO JHE (05.10.2020): Probably missing on Service API -> mavenArtifactsToBuild
        def artefacts = service.mavenArtifactsToBuild;
        def listsByDepLevel = artefacts.groupBy { it.dependencyLevel }
        def depLevels = listsByDepLevel.keySet() as List
        depLevels.sort()
        depLevels.reverse(true)
        log(depLevels, "buildAndReleaseModulesConcurrent")
        depLevels.each { depLevel ->
            def artifactsToBuildParallel = listsByDepLevel[depLevel]
            log(artifactsToBuildParallel, "buildAndReleaseModulesConcurrent")
            def parallelBuilds = artifactsToBuildParallel.collectEntries {
                ["Building Level: ${it.dependencyLevel} and Module: ${it.name}": buildAndReleaseModulesConcurrent(patchConfig, it)]
            }
            parallel parallelBuilds
        }
    }
}

def buildAndReleaseModulesConcurrent(patchConfig,module) {
    return {
        node {
            def tag = tagName(patchConfig)
            coFromTagCvsConcurrent(tag,module.name)
            // JHE (06.10.2020): Probably we can ignore this step
            //coIt21BundleFromBranchCvs(patchConfig)

            // TODO JHE (06.10.2020) : uncomment and implement this one
            // buildAndReleaseModule(patchConfig,module)
        }
    }
}

def buildAndReleaseModule(patchConfig,module) {
    log("buildAndReleaseModule : " + module.name,"buildAndReleaseModule")
    releaseModule(patchConfig,module)
    buildModule(patchConfig,module)
    updateBom(patchConfig,module)
    log("buildAndReleaseModule : " + module.name,"buildAndReleaseModule")
}

// TODO (che, 29.10) not very efficient
def coFromTagCvsConcurrent(tag,module) {
    lock ("ConcurrentCvsCheckout") {
        coFromTagcvs(tag, module)
    }
}

def coFromTagcvs(tag, moduleName) {
    def callBack = benchmark()
    def duration = callBack {
        checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
                [compressionLevel: -1, cvsRoot: env.CVS_ROOT, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [
                        [location: [$class: 'TagRepositoryLocation', tagName: tag, useHeadIfNotFound: false],  modules: [
                                [localName: moduleName, remoteName: moduleName]
                        ]]
                ]]
        ], skipChangeLog: false])
    }
    log("Checkout of ${moduleName} took ${duration} ms","coFromTagcvs")
}

def tagName(patchConfig) {
    if (patchConfig.patchTag?.trim()) {
        patchConfig.patchTag
    } else {
        patchConfig.developerBranch
    }
}

def publishNewRevisionFor(service) {
    dir(service.packagerName) {
        def cmd = "./gradlew clean publish -PnewRevision -PtargetHost=dev-jhe.light.apgsga.ch -PinstallTarget=dev-jhe -PpatchFilePath=/var/opt/apg-patch-service-server/db/Patch2222.json -PbuildType=PATCH -Dgradle.user.home=/var/jenkins/gradle/home --stacktrace --info"
        def result = sh ( returnStdout : true, script: cmd).trim()
        println "result of ${cmd} : ${result}"
    }
}

def coFromBranchCvs(cvsBranch, moduleName) {
    def callBack = benchmark()
    def duration = callBack {
        checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
                [compressionLevel: -1, cvsRoot: env.CVS_ROOT, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [
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