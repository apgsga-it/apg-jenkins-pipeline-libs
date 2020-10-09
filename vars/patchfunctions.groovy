#!groovy

def patchBuildsConcurrent(patchConfig) {
    node {
        parallel 'java build': {
            // TODO JHE (05.10.2020): do we want to parallelize service build as well ? maybe not a prio in this first release
            patchConfig.services.each { service ->
                (
                        lock("${service.serviceName}-${patchConfig.currentTarget}-Build") {
                            deleteDir()
                            checkoutAndStashPackager(service)
                            publishNewRevisionFor(service, patchConfig)
                            buildAndReleaseModulesConcurrent(service, patchConfig.currentTarget, tagName(patchConfig))
                        }
                )
            }
        }, 'DB Build': {
            lock("dbBuild-${patchConfig.currentTarget}-Build") {
                deleteDir()
                coDbModules(patchConfig)
                dbAssemble(patchConfig)
            }
        }
    }
}

def dbAssemble(patchConfig) {
    def PatchDbFolderName = getCoPatchDbFolderName(patchConfig)
    fileOperations ([
            folderCreateOperation(folderPath: "${PatchDbFolderName}\\config")
    ])
    // Done in order for the config folder to be taken into account when we create the ZIP...
    fileOperations ([
            fileCreateOperation(fileName: "${PatchDbFolderName}\\config\\dummy.txt", fileContent: "")
    ])
    def cmPropertiesContent = "config_name:${PatchDbFolderName}\r\npatch_name:${PatchDbFolderName}\r\ntag_name:${PatchDbFolderName}"
    fileOperations ([
            fileCreateOperation(fileName: "${PatchDbFolderName}\\cm_properties.txt", fileContent: cmPropertiesContent)
    ])
    def configInfoContent = "config_name:${PatchDbFolderName}"
    fileOperations ([
            fileCreateOperation(fileName: "${PatchDbFolderName}\\config_info.txt", fileContent: configInfoContent)
    ])

    def installPatchContent = "@echo off\r\n"
    // TODO (jhe) :  0900C info doesn't exist at the moment witin patchConfig... also datetime ... do we have it somewhere?
    installPatchContent += "@echo *** Installation von Patch 0900C_${patchConfig.patchNummer} [Build von TODO get YYYY/MM/dd-HH:mm:ss]\r\n"
    installPatchContent += "set /p v_params=Geben Sie die Zielumgebung ein: \r\n"
    installPatchContent += "pushd %~dp0 \r\n\r\n"
    installPatchContent += "cmd /c \\\\cm-linux.apgsga.ch\\cm_ui\\it21_patch.bat %v_params%\r\n"
    installPatchContent += "popd"
    fileOperations ([
            fileCreateOperation(fileName: "${PatchDbFolderName}\\install_patch.bat", fileContent: installPatchContent)
    ])

    publishDbAssemble(patchConfig)
}

def coDbModules(patchConfig) {
    def dbObjects = patchConfig.dbObjectsAsVcsPath
    log("Following DB Objects should get checked out : ${dbObjects}","coDbModules")

    def patchDbFolderName = getCoPatchDbFolderName(patchConfig)
    fileOperations ([
            folderDeleteOperation(folderPath: "${patchDbFolderName}")
    ])
    fileOperations ([
            folderCreateOperation(folderPath: "${patchDbFolderName}")
    ])
    /*
    ** work-around for not yet existing packaging of db scripts, see ticket CM-216
    */
    fileOperations ([
            folderCreateOperation(folderPath: "${patchDbFolderName}/oracle")
    ])

    def patchNumber = patchConfig.patchNummer
    def dbPatchTag = patchConfig.patchTag

    log("DB Objects for patch \"${patchNumber}\" being checked out to \"${patchDbFolderName}/oracle\"","coDbModule")
    patchConfig.dbObjects.collect{it.moduleName}.unique().each { dbModule ->
        log("- module \"${dbModule}\" tag \"${dbPatchTag}\" being checked out","coDbModule")
        dir("${patchDbFolderName}/oracle") {
            def moduleDirectory = dbModule.replace(".","_")
            sh "cvs -d${env.CVS_ROOT} co -r${dbPatchTag} -d${moduleDirectory} ${dbModule}"
        }
    }
    log("DB Objects for patch \"${patchNumber}\" checked out","coDbModule")

}

def getCoPatchDbFolderName(patchConfig) {
    // TODO JHE (08.10.2020): Probably we don't want to replace the "Patch" anymore, what was the purpose of it ?!?!
    return "${patchConfig.dbPatchBranch.replace('Patch', 'test-jhe')}-${patchConfig.revisionMnemoPart}-${patchConfig.revision}"
}

def checkoutAndStashPackager(service) {
    // TODO JHE (05.10.2020) : service.packagerName needs to be implemented in Piper
    coFromBranchCvs(service.microServiceBranch,service.packagerName)
    dir(service.packagerName) {
        stash includes: "**/*", name: packagerStashNameFor(service)
    }
}

def packagerStashNameFor(service) {
    return service.packagerName.replaceAll("/","")
}

def buildAndReleaseModulesConcurrent(service,target,tag) {
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
                ["Building Level: ${it.dependencyLevel} and Module: ${it.name}": buildAndReleaseModulesConcurrent(tag, it, target, service)]
            }
            parallel parallelBuilds
        }
}

def buildAndReleaseModulesConcurrent(tag, module, target, service) {
    return {
        node {
            coFromTagCvsConcurrent(tag,module.name)
            buildAndReleaseModule(module,service,target)
        }
    }
}

def buildAndReleaseModule(module,service,target) {
    def revision = commonPatchFunctions.getRevisionFor(service,target)
    def mavenVersionNumber = mavenVersionNumber(service,revision)
    log("buildAndReleaseModule : " + module.name,"buildAndReleaseModule")
    releaseModule(module,revision,service.revisionMnemoPart, mavenVersionNumber)
    buildModule(module,mavenVersionNumber)
    updateBom(service,target,module,mavenVersionNumber)
}

// TODO JHE (07.10.2020): to be checked here, do we want to publish the bom on Artifactory ? Or enough to have it within MavenLocal?
def updateBom(service,target,module,mavenVersionNumber) {
    lock ("BomUpdate${mavenVersionNumber}") {
        dir("gradlePackagerProject") {
            unstash packagerStashNameFor(service)
            def cmd = "./gradlew publish -PbomBaseVersion=${bomBaseVersionFor(service)} -PinstallTarget=${target} -PupdateArtifact=${module.groupId}:${module.artifactId}:${mavenVersionNumber} -Dgradle.user.home=${env.GRADLE_USER_HOME_PATH}  --stacktrace --info"
            def result = sh ( returnStdout : true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
        }
    }
}

def buildModule(module,buildVersion) {
    dir ("${module.name}") {
        log("Building Module : " + module.name + " for Version: " + buildVersion,"buildModule")
        // TODO JHE (06.10.2020): get active profile via env properties, or activate a default within settings.xml
        // TODO JHE (08.10.2020): to be checked if we want to install or deploy. Probably OK if it stays only on MavenLocal
        def mvnCommand = "mvn -DbomVersion=${buildVersion} -Partifactory-jhe clean install"
        log("${mvnCommand}","buildModule")
        lock ("BomUpdate${buildVersion}") {
            withMaven( maven: 'apache-maven-3.2.5') { sh "${mvnCommand}" }
        }
    }
}

def releaseModule(module,revision,revisionMnemoPart, mavenVersionNumber) {
    dir ("${module.name}") {
        log("Releasing Module : " + module.name + " for Revision: " + revision + " and: " +  revisionMnemoPart, "releaseModule")
        def buildVersion =  mavenVersionNumber
        log("BuildVersion = ${buildVersion}","releaseModule")
        def mvnCommand = "mvn -DbomVersion=${buildVersion}" + ' clean build-helper:parse-version versions:set -DnewVersion=\\${parsedVersion.majorVersion}.\\${parsedVersion.minorVersion}.\\${parsedVersion.incrementalVersion}.' + revisionMnemoPart + '-' + revision
        log("${mvnCommand}","releaseModule")
        withMaven( maven: 'apache-maven-3.2.5') { sh "${mvnCommand}" }
    }
}

def mavenVersionNumber(service,revision) {
    def mavenVersion = revision?.trim() ? service.baseVersionNumber + "." + service.revisionMnemoPart + "-" + revision : service.baseVersionNumber + "." + service.revisionMnemoPart
    println "mavenVersionNumber = ${mavenVersion}"
    return mavenVersion
}

// TODO (che, 29.10) not very efficient
def coFromTagCvsConcurrent(tag,moduleName) {
    lock ("ConcurrentCvsCheckout") {
        coFromTagcvs(tag, moduleName)
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

def publishNewRevisionFor(service,patchConfig) {
    dir("servicePackagerProject") {
        unstash packagerStashNameFor(service)
        def cmd = "./gradlew clean publish -PnewRevision -PbomBaseVersion=${bomBaseVersionFor(service)} -PinstallTarget=${patchConfig.currentTarget} -PpatchFilePath=${env.PATCH_DB_FOLDER}/Patch${patchConfig.patchNummer}.json -PbuildType=PATCH -Dgradle.user.home=${env.GRADLE_USER_HOME_PATH} --stacktrace --info"
        def result = sh ( returnStdout : true, script: cmd).trim()
        println "result of ${cmd} : ${result}"
    }
}

def bomBaseVersionFor(service) {
    def bbv = service.baseVersionNumber + "." + service.revisionMnemoPart
    log("bomBaseVersion = ${bbv}, for service = ${service}", "bomBaseVersionFor")
    return bbv
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