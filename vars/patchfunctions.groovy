#!groovy

def patchBuildsConcurrent(jsonParam, revisionClonedPath) {
    if (javaBuildRequired(jsonParam)) {
        // JHE (31.05.2021) : This lock will obviously slow down the build process. However, we want to be 100% sure that patches with mix of different services won't
        //                    take eachother over, and publish new revisions for services too early.
        lock("${jsonParam.target}-Build") {
            commonPatchFunctions.logPatchActivity(jsonParam.patchNumber, jsonParam.target, "build", "started",jsonParam.buildUrl == null ? logMessageForOldPipelinePatch() : jsonParam.buildUrl)
            // TODO JHE (05.10.2020): We could build service in parallel, but not a priority for the first release
            jsonParam.services.each { service ->
                (

                        lock("${service.serviceName}-${jsonParam.target}-Build") {
                            commonPatchFunctions.log("Building following service : ${service}", "patchBuildsConcurrent")
                            publishNewRevisionFor(service, jsonParam.patchNumber, jsonParam.target, revisionClonedPath)
                            buildAndReleaseModulesConcurrent(service, jsonParam, revisionClonedPath)
                            updateBomForNonBuiltArtifacts(service, jsonParam, revisionClonedPath)

                            lock("mergeRevisionInformation") {
                                mergeRevisionToMainJson(service, jsonParam.patchNumber, jsonParam.target, revisionClonedPath)
                            }
                        }
                )
            }
            commonPatchFunctions.logPatchActivity(jsonParam.patchNumber, jsonParam.target, "build", "done", jsonParam.buildUrl == null ? logMessageForOldPipelinePatch() : jsonParam.buildUrl)
        }
    }
}

def logMessageForOldPipelinePatch() {
    return "Not supported for old Pipeline Patches"
}

def updateBomForNonBuiltArtifacts(service, jsonParam, revisionClonedPath) {
    def revision = commonPatchFunctions.getRevisionFor(service, jsonParam.target, revisionClonedPath)
    def mavenVersionNumber = mavenVersionNumber(service, revision)
    getNonBuildArtifact(service,jsonParam).each {artifact ->
        updateBom(service, jsonParam.target, artifact, artifact.version, revisionClonedPath)
    }

}

def getNonBuildArtifact(service,jsonParam) {
    def artifactList = []
    service.artifactsToPatch.each {artifactsToPatch ->
        def addArtifact = true
        jsonParam.artifactsToBuild."${service.serviceName}".each{artifactsToBuild ->
            if(isSameArtifact(artifactsToPatch,artifactsToBuild)) {
                addArtifact = false
            }
        }
        if(addArtifact) {
            artifactList.add(artifactsToPatch)
        }
    }

    return artifactList
}

def isSameArtifact(art_1,art_2) {
    return art_1.artifactId.equals(art_2.artifactId) &&
            art_1.groupId.equals(art_2.groupId)
}

def javaBuildRequired(jsonParam) {
    return !jsonParam.services.isEmpty()
}

def patchBuildDbZip(jsonParam) {
    if (dbBuildRequired(jsonParam)) {
        lock("dbBuild-${jsonParam.target}-Build") {
            commonPatchFunctions.logPatchActivity(jsonParam.patchNumber, jsonParam.target, "db-build", "started", jsonParam.buildUrl == null ? logMessageForOldPipelinePatch() : jsonParam.buildUrl)
            coDbModules(jsonParam)
            buildDbZip(jsonParam)
            commonPatchFunctions.logPatchActivity(jsonParam.patchNumber, jsonParam.target, "db-build", "done", jsonParam.buildUrl == null ? logMessageForOldPipelinePatch() : jsonParam.buildUrl)
        }
    }
}

def dbBuildRequired(jsonParam) {
    return !jsonParam.dbObjects.isEmpty()
}

def buildDbZip(jsonParam) {
    def patchDbFolderName = getCoPatchDbFolderName(jsonParam)
    def zipName = "${patchDbFolderName}.zip"
    fileOperations([
            fileDeleteOperation(includes: zipName)
    ])
    zip zipFile: zipName, dir: "${patchDbFolderName}"
    fileOperations([
            fileCopyOperation(includes: zipName, targetLocation: env.DBZIPS_FILE_PATH)
    ])
}

def coDbModules(jsonParam) {
    def dbObjects = jsonParam.dbObjectsAsVcsPath
    commonPatchFunctions.log("Following DB Objects should get checked out : ${dbObjects}", "coDbModules")

    def patchDbFolderName = getCoPatchDbFolderName(jsonParam)
    fileOperations([
            folderDeleteOperation(folderPath: "${patchDbFolderName}")
    ])
    fileOperations([
            folderCreateOperation(folderPath: "${patchDbFolderName}")
    ])
    /*
    ** work-around for not yet existing packaging of db scripts, see ticket CM-216
    */
    fileOperations([
            folderCreateOperation(folderPath: "${patchDbFolderName}/oracle")
    ])

    def patchNumber = jsonParam.patchNumber
    def dbPatchTag = jsonParam.dbPatchTag

    commonPatchFunctions.log("DB Objects for patch \"${patchNumber}\" being checked out to \"${patchDbFolderName}/oracle\"", "coDbModule")
    jsonParam.dbObjects.collect { it.moduleName }.unique().each { dbModule ->
        commonPatchFunctions.log("- module \"${dbModule}\" tag \"${dbPatchTag}\" being checked out", "coDbModule")
        dir("${patchDbFolderName}/oracle") {
            def moduleDirectory = dbModule.replace(".", "_")
            sh "cvs -d${env.CVS_ROOT} co -r${dbPatchTag} -d${moduleDirectory} ${dbModule}"
        }
    }
    commonPatchFunctions.log("DB Objects for patch \"${patchNumber}\" checked out", "coDbModule")

}

def getCoPatchDbFolderName(jsonParam) {
    return "${jsonParam.dbPatchBranch}_${jsonParam.target}"
}


def buildAndReleaseModulesConcurrent(service, jsonParam, revisionRootPath) {
    def tag = tagName(service, jsonParam)
    def target = jsonParam.target
    def artefactsToBuild = jsonParam.artifactsToBuild."${service.serviceName}"
    def listsByDepLevel = artefactsToBuild.groupBy { it.dependencyLevel }
    def depLevels = listsByDepLevel.keySet() as List
    depLevels.sort()
    depLevels.reverse(true)
    commonPatchFunctions.log(depLevels, "buildAndReleaseModulesConcurrent")
    depLevels.each { depLevel ->
        def artifactsToBuildParallel = listsByDepLevel[depLevel]
        commonPatchFunctions.log(artifactsToBuildParallel, "buildAndReleaseModulesConcurrent")
        def parallelBuilds = artifactsToBuildParallel.collectEntries {
            ["Building Level: ${it.dependencyLevel} and Module: ${it.name}": buildAndReleaseModulesConcurrent(tag, it, target, service, revisionRootPath)]
        }
        parallel parallelBuilds
    }
}

def buildAndReleaseModulesConcurrent(tag, module, target, service, revisionRootPath) {
    return {
        coFromTagCvsConcurrent(tag, module.name)
        buildAndReleaseModule(module, service, target, revisionRootPath)
    }
}

def buildAndReleaseModule(module, service, target, revisionRootPath) {
    def revision = commonPatchFunctions.getRevisionFor(service, target, revisionRootPath)
    def mavenVersionNumber = mavenVersionNumber(service, revision)
    commonPatchFunctions.log("buildAndReleaseModule : " + module.name, "buildAndReleaseModule")
    releaseModule(module, revision, service.serviceMetaData.revisionMnemoPart, mavenVersionNumber)
    buildModule(module, mavenVersionNumber)
    updateBom(service, target, module, mavenVersionNumber, revisionRootPath)
}

// artifactNewVersion can be different when updating a framework library for which the version is fix
// however, within the function, we get the mavenVersionNumber to ensure we sequentially update a bom for a specific revision
def updateBom(service, target, module, artifactNewVersion, revisionRootPath) {
    def revision = commonPatchFunctions.getRevisionFor(service, target, revisionRootPath)
    def mavenVersionNumber = mavenVersionNumber(service, revision)
    lock("BomUpdate${mavenVersionNumber}") {
        commonPatchFunctions.log("updateBom for service : ${service} / on target ${target}")
        commonPatchFunctions.coFromBranchCvs(service.serviceMetaData.microServiceBranch, service.serviceMetaData.revisionPkgName)
        dir(service.serviceMetaData.revisionPkgName) {
            sh "chmod +x ./gradlew"
            def cmd = "./gradlew publish -PrevisionRootPath=${revisionRootPath} -PbomBaseVersion=${bomBaseVersionFor(service)} -PinstallTarget=${target} -PupdateArtifact=${module.groupId}:${module.artifactId}:${artifactNewVersion} ${env.GRADLE_OPTS} --info --stacktrace"
            commonPatchFunctions.runShCommandWithRetry(cmd,5,60)
        }
    }
}

def buildModule(module, buildVersion) {
    dir("${module.name}") {
        commonPatchFunctions.log("Building Module : " + module.name + " for Version: " + buildVersion, "buildModule")
        def mvnCommand = "mvn -DbomVersion=${buildVersion} clean deploy ${env.MAVEN_PROFILE}"
        commonPatchFunctions.log("${mvnCommand}", "buildModule")
        lock("BomUpdate${buildVersion}") {
            withMaven(maven: 'Default') {
                commonPatchFunctions.runShCommandWithRetry(mvnCommand,5,60)
            }
        }
    }
}

def releaseModule(module, revision, revisionMnemoPart, mavenVersionNumber) {
    dir("${module.name}") {
        commonPatchFunctions.log("Releasing Module : " + module.name + " for Revision: " + revision + " and revisionMnemoPart " + revisionMnemoPart, "releaseModule")
        def buildVersion = mavenVersionNumber
        commonPatchFunctions.log("BuildVersion = ${buildVersion}", "releaseModule")
        def mvnCommand = "mvn ${env.MAVEN_PROFILE} -DbomVersion=${buildVersion}" + ' clean build-helper:parse-version versions:set -DnewVersion=\\${parsedVersion.majorVersion}.\\${parsedVersion.minorVersion}.\\${parsedVersion.incrementalVersion}.' + revisionMnemoPart + '-' + revision
        commonPatchFunctions.log("${mvnCommand}", "releaseModule")
        withMaven(maven: 'Default') {
            commonPatchFunctions.runShCommandWithRetry(mvnCommand,5,60)
        }
    }
}

def mavenVersionNumber(service, revision) {
    def mavenVersion = revision?.trim() ? service.serviceMetaData.baseVersionNumber + "." + service.serviceMetaData.revisionMnemoPart + "-" + revision : service.serviceMetaData.baseVersionNumber + "." + service.serviceMetaData.revisionMnemoPart + "-SNAPSHOT"
    println "mavenVersionNumber = ${mavenVersion}"
    return mavenVersion
}

// TODO (che, 29.10) not very efficient
def coFromTagCvsConcurrent(tag, moduleName) {
    lock("ConcurrentCvsCheckout") {
        coFromTagcvs(tag, moduleName)
    }
}

def coFromTagcvs(tag, moduleName) {
    def callBack = commonPatchFunctions.benchmark()
    def duration = callBack {
        checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
                [compressionLevel: -1, cvsRoot: env.CVS_ROOT, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [
                        [location: [$class: 'TagRepositoryLocation', tagName: tag, useHeadIfNotFound: false], modules: [
                                [localName: moduleName, remoteName: moduleName]
                        ]]
                ]]
        ], skipChangeLog      : false])
    }
    commonPatchFunctions.log("Checkout of ${moduleName} took ${duration} ms", "coFromTagcvs")
}

def tagName(service, jsonParam) {
    if (service.patchTag?.trim()) {
        service.patchTag
    } else {
        jsonParam.developerBranch
    }
}

def mergeRevisionToMainJson(service, patchNumber, target, revisionRootPath) {
    commonPatchFunctions.log("Merging revision number for ${service.serviceName} and patch ${patchNumber}", "mergeRevisionToMainJson")
    commonPatchFunctions.coFromBranchCvs(service.serviceMetaData.microServiceBranch, service.serviceMetaData.revisionPkgName)
    dir(service.serviceMetaData.revisionPkgName) {
        sh "chmod +x ./gradlew"
        def cmd = "./gradlew clean mergeRevision -PrevisionRootPath=${revisionRootPath} -PinstallTarget=${target} -PpatchFilePath=${env.PATCH_DB_FOLDER}/Patch${patchNumber}.json ${env.GRADLE_OPTS} --stacktrace --info"
        commonPatchFunctions.log("Following will be executed : ${cmd}", "mergeRevisionToMainJson")
        def result = sh(returnStdout: true, script: cmd).trim()
        println "result of ${cmd} : ${result}"
    }
}

def publishNewRevisionFor(service, patchNumber, target, revisionRootPath) {
    commonPatchFunctions.log("publishing new revision for service ${service} for patchNumber=${patchNumber} on target=${target}", "publishNewRevisionFor")
    commonPatchFunctions.log("Switching into following folder : ${service.serviceMetaData.revisionPkgName}", "publishNewRevisionFor")
    commonPatchFunctions.coFromBranchCvs(service.serviceMetaData.microServiceBranch, service.serviceMetaData.revisionPkgName)
    lock("publishNewRevision") {
        dir(service.serviceMetaData.revisionPkgName) {
            sh "chmod +x ./gradlew"
            def cmd = "./gradlew clean publish -PnewRevision -PpatchRevisionRootPath=${revisionRootPath} -PbomBaseVersion=${bomBaseVersionFor(service)} -PinstallTarget=${target} -PpatchFilePath=${env.PATCH_DB_FOLDER}/Patch${patchNumber}.json ${env.GRADLE_OPTS} --stacktrace --info"
            commonPatchFunctions.log("Following will be executed : ${cmd}", "publishNewRevisionFor")
            commonPatchFunctions.runShCommandWithRetry(cmd,5,60)
        }
    }
}

def bomBaseVersionFor(service) {
    def bbv = service.serviceMetaData.baseVersionNumber + "." + service.serviceMetaData.revisionMnemoPart
    commonPatchFunctions.log("bomBaseVersion = ${bbv}, for service = ${service}", "bomBaseVersionFor")
    return bbv
}