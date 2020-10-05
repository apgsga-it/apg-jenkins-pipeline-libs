import com.apgsga.revision.manager.domain.RevisionManagerBuilder

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

        patchConfig.services.each { service -> (
            lock("${service.serviceName}-${patchConfig.currentTarget}-Build") {
                deleteDir()
                // TODO JHE (05.10.2020) : service.packagerName needs to be implemented in Piper
                coFromBranchCvs(service.microServiceBranch,service.packagerName)

                nextRevision(service)
                def lastRevision = RevisionManagerBuilder.create().build().lastRevision(service.serviceName,"dev-jhe")
                println "lastRevision for ${service.serviceName} on dev-jhe = ${lastRevision}"

                // service.revisionNumber = lastRevision

                /*
                generateVersionProperties(patchConfig)
                buildAndReleaseModulesConcurrent(patchConfig)
                saveRevisions(patchConfig)
                 */
            }
       )}
    }
}

def nextRevision(service) {
    setPatchRevision(service)
    //setPatchLastRevision(patchConfig)
}

def setPatchRevision(service) {

    def cmd

    dir(service.packagerName) {
        cmd = "./gradlew clean publish -PnewRevision -PtargetHost=dev-jhe.light.apgsga.ch -PinstallTarget=dev-jhe -PpatchFilePath=/var/opt/apg-patch-service-server/db/Patch2222.json -PbuildType=PATCH -Dgradle.user.home=/var/jenkins/gradle/home --stacktrace --info"
        def result = sh ( returnStdout : true, script: cmd).trim()
        println "result of ${cmd} : ${result}"





        // TODO JHE (05.10.2020) : will be useful when releasing modules, probably too early here
        /*
        service.mavenArtifacts.each {
            cmd = "./gradlew clean publish -PupdateArtifact=com.apgsga.testapp:testapp-module:testjheVersioniuiu -PinstallTarget=dev-jhe -PbuildType=PATCH -Dgradle.user.home=/var/jenkins/gradle/home --info"
        }

         */

    }

    /*
    def cmd = "/opt/apg-patch-cli/bin/apsrevcli.sh -nr"
    def revision = sh ( returnStdout : true, script: cmd).trim()
    patchConfig.revision = revision
    log("patchConfig.revision has been set with ${revision}","setPatchRevision")

     */
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