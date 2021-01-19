#!groovy

def patchBuildsConcurrent(jsonParam) {
    node {
            if(javaBuildRequired(jsonParam)) {
                commonPatchFunctions.logPatchActivity(jsonParam.patchNumber,jsonParam.target,"build","started")
                // TODO JHE (05.10.2020): We could build service in parallel, but not a priority for the first release
                jsonParam.services.each { service ->
                    (
                            lock("${service.serviceName}-${jsonParam.target}-Build") {
                                commonPatchFunctions.log("Building following service : ${service}", "patchBuildsConcurrent")
                                deleteDir()
                                //checkoutPackager(service)
                                publishNewRevisionFor(service, jsonParam.patchNumber, jsonParam.target)
                                buildAndReleaseModulesConcurrent(service, jsonParam.target, tagName(service, jsonParam))
                            }
                    )
                }
                commonPatchFunctions.logPatchActivity(jsonParam.patchNumber,jsonParam.target,"build","done")
            }
    }
}

def javaBuildRequired(jsonParam) {
    return !jsonParam.services.isEmpty()
}

def patchBuildDbZip(jsonParam) {
    if(dbBuildRequired(jsonParam)) {
        lock("dbBuild-${jsonParam.target}-Build") {
            commonPatchFunctions.logPatchActivity(jsonParam.patchNumber,jsonParam.target,"db-build","started")
            deleteDir()
            coDbModules(jsonParam)
            dbBuild(jsonParam)
            buildDbZip(jsonParam)
            commonPatchFunctions.logPatchActivity(jsonParam.patchNumber,jsonParam.target,"db-build","done")
        }
    }
}

def dbBuildRequired(jsonParam) {
    return !jsonParam.dockerServices.isEmpty() || !jsonParam.dbObjects.isEmpty()
}

def buildDbZip(jsonParam) {
    def patchDbFolderName = getCoPatchDbFolderName(jsonParam)
    def zipName = "${patchDbFolderName}.zip"
    fileOperations ([
            fileDeleteOperation(includes: zipName)
    ])
    zip zipFile: zipName, glob: "${patchDbFolderName}/**"
    fileOperations ([
            fileCopyOperation(includes: zipName, targetLocation: env.DBZIPS_FILE_PATH)
    ])
}

def dbBuild(jsonParam) {
    def PatchDbFolderName = getCoPatchDbFolderName(jsonParam)
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
    installPatchContent += "@echo *** Installation von Patch 0900C_${jsonParam.patchNumber} [Build von TODO get YYYY/MM/dd-HH:mm:ss]\r\n"
    installPatchContent += "set /p v_params=Geben Sie die Zielumgebung ein: \r\n"
    installPatchContent += "pushd %~dp0 \r\n\r\n"
    installPatchContent += "cmd /c \\\\cm-linux.apgsga.ch\\cm_ui\\it21_patch.bat %v_params%\r\n"
    installPatchContent += "popd"
    fileOperations ([
            fileCreateOperation(fileName: "${PatchDbFolderName}\\install_patch.bat", fileContent: installPatchContent)
    ])


}

def coDbModules(jsonParam) {
    def dbObjects = jsonParam.dbObjectsAsVcsPath
    commonPatchFunctions.log("Following DB Objects should get checked out : ${dbObjects}","coDbModules")

    def patchDbFolderName = getCoPatchDbFolderName(jsonParam)
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

    def patchNumber = jsonParam.patchNumber
    def dbPatchTag = jsonParam.dbPatch.patchTag

    commonPatchFunctions.log("DB Objects for patch \"${patchNumber}\" being checked out to \"${patchDbFolderName}/oracle\"","coDbModule")
    jsonParam.dbObjects.collect{it.moduleName}.unique().each { dbModule ->
        commonPatchFunctions.log("- module \"${dbModule}\" tag \"${dbPatchTag}\" being checked out","coDbModule")
        dir("${patchDbFolderName}/oracle") {
            def moduleDirectory = dbModule.replace(".","_")
            sh "cvs -d${env.CVS_ROOT} co -r${dbPatchTag} -d${moduleDirectory} ${dbModule}"
        }
    }
    commonPatchFunctions.log("DB Objects for patch \"${patchNumber}\" checked out","coDbModule")

}

def getCoPatchDbFolderName(jsonParam) {
    return "${jsonParam.dbPatchBranch}_${jsonParam.target}"
}

def checkoutPackager(service) {
    service.serviceMetaData.packages.each{pack ->
        commonPatchFunctions.coFromBranchCvs(service.serviceMetaData.microServiceBranch,pack.packagerName)
    }
}

def buildAndReleaseModulesConcurrent(service,target,tag) {
        def artefacts = service.artifactsToPatch
        def listsByDepLevel = artefacts.groupBy { it.dependencyLevel }
        def depLevels = listsByDepLevel.keySet() as List
        depLevels.sort()
        depLevels.reverse(true)
        commonPatchFunctions.log(depLevels, "buildAndReleaseModulesConcurrent")
        depLevels.each { depLevel ->
            def artifactsToBuildParallel = listsByDepLevel[depLevel]
            commonPatchFunctions.log(artifactsToBuildParallel, "buildAndReleaseModulesConcurrent")
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
    commonPatchFunctions.log("buildAndReleaseModule : " + module.name,"buildAndReleaseModule")
    releaseModule(module,revision,service.serviceMetaData.revisionMnemoPart, mavenVersionNumber)
    buildModule(module,mavenVersionNumber)
    updateBom(service,target,module,mavenVersionNumber)
}

def updateBom(service,target,module,mavenVersionNumber) {
    lock ("BomUpdate${mavenVersionNumber}") {

        commonPatchFunctions.log("updateBom for service : ${service} / on target ${target}")

        service.serviceMetaData.packages.each{pack ->
            commonPatchFunctions.coFromBranchCvs(service.serviceMetaData.microServiceBranch,pack.packagerName)
            dir(pack.packagerName) {
                def cmd = "./gradlew publish -PbomBaseVersion=${bomBaseVersionFor(service)} -PinstallTarget=${target} -PupdateArtifact=${module.groupId}:${module.artifactId}:${mavenVersionNumber} ${env.GRADLE_OPTS} --info --stacktrace"
                def result = sh ( returnStdout : true, script: cmd).trim()
                println "result of ${cmd} : ${result}"
            }
        }
    }
}

def buildModule(module,buildVersion) {
    dir ("${module.name}") {
        commonPatchFunctions.log("Building Module : " + module.name + " for Version: " + buildVersion,"buildModule")
        // TODO JHE (08.10.2020): should we deploy to Artifactory -> IT-36781
        def mvnCommand = "mvn -DbomVersion=${buildVersion} ${env.MAVEN_PROFILE} clean deploy"
        commonPatchFunctions.log("${mvnCommand}","buildModule")
        lock ("BomUpdate${buildVersion}") {
            withMaven( maven: 'Default') { sh "${mvnCommand}" }
        }
    }
}

def releaseModule(module,revision,revisionMnemoPart,mavenVersionNumber) {
    dir ("${module.name}") {
        commonPatchFunctions.log("Releasing Module : " + module.name + " for Revision: " + revision + " and revisionMnemoPart " +  revisionMnemoPart, "releaseModule")
        def buildVersion =  mavenVersionNumber
        commonPatchFunctions.log("BuildVersion = ${buildVersion}","releaseModule")
        def mvnCommand = "mvn ${env.MAVEN_PROFILE} -DbomVersion=${buildVersion}" + ' clean build-helper:parse-version versions:set -DnewVersion=\\${parsedVersion.majorVersion}.\\${parsedVersion.minorVersion}.\\${parsedVersion.incrementalVersion}.' + revisionMnemoPart + '-' + revision
        commonPatchFunctions.log("${mvnCommand}","releaseModule")
        withMaven( maven: 'Default') { sh "${mvnCommand}" }
    }
}

def mavenVersionNumber(service,revision) {
    def mavenVersion = revision?.trim() ? service.serviceMetaData.baseVersionNumber + "." + service.serviceMetaData.revisionMnemoPart + "-" + revision : service.serviceMetaData.baseVersionNumber + "." + service.serviceMetaData.revisionMnemoPart + "-SNAPSHOT"
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
    def callBack = commonPatchFunctions.benchmark()
    def duration = callBack {
        checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
                [compressionLevel: -1, cvsRoot: env.CVS_ROOT, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [
                        [location: [$class: 'TagRepositoryLocation', tagName: tag, useHeadIfNotFound: false],  modules: [
                                [localName: moduleName, remoteName: moduleName]
                        ]]
                ]]
        ], skipChangeLog: false])
    }
    commonPatchFunctions.log("Checkout of ${moduleName} took ${duration} ms","coFromTagcvs")
}

def tagName(service,jsonParam) {
    if (service.patchTag?.trim()) {
        service.patchTag
    } else {
        jsonParam.developerBranch
    }
}

def publishNewRevisionFor(service,patchNumber,target) {
    commonPatchFunctions.log("publishing new revision for service ${service} for patchNumber=${patchNumber} on target=${target}","publishNewRevisionFor")
    //TODO JHE (07.01.2021): Depending on the implementation of IT-36715, we might be able to remove this lock
    lock("revisionFileOperation") {
        service.serviceMetaData.packages.each{pack ->
            commonPatchFunctions.log("Switching into following folder : ${pack.packagerName}","publishNewRevisionFor")
            commonPatchFunctions.coFromBranchCvs(service.serviceMetaData.microServiceBranch,pack.packagerName)
            dir(pack.packagerName) {
                sh "chmod +x -./gradlew"
                def cmd = "./gradlew clean publish -PnewRevision -PbomBaseVersion=${bomBaseVersionFor(service)} -PinstallTarget=${target} -PpatchFilePath=${env.PATCH_DB_FOLDER}/Patch${patchNumber}.json -PbuildType=PATCH ${env.GRADLE_OPTS} --stacktrace --info"
                commonPatchFunctions.log("Following will be executed : ${cmd}","publishNewRevisionFor")
                def result = sh(returnStdout: true, script: cmd).trim()
                println "result of ${cmd} : ${result}"
            }
        }
    }
}

def bomBaseVersionFor(service) {
    def bbv = service.serviceMetaData.baseVersionNumber + "." + service.serviceMetaData.revisionMnemoPart
    commonPatchFunctions.log("bomBaseVersion = ${bbv}, for service = ${service}", "bomBaseVersionFor")
    return bbv
}