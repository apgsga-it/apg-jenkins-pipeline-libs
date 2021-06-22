#!groovy

def assembleAndDeployJavaService(parameter) {
    if(parameter.packagers.size() > 0) {
        def revisionClonedPath = pwd()
        lock("mergeRevisionInformation") {
                commonPatchFunctions.copyRevisionFilesTo(revisionClonedPath)
        }
        checkoutPackagerProjects(parameter.packagers)
        doAssembleAndDeploy(parameter,revisionClonedPath)
    }
    else {
        commonPatchFunctions.log("No Java Services to be assembled!","assembleAndDeployJavaService")
    }
}

def assembleAndDeployDb(parameter) {
    if(parameter.dbZipNames.size() > 0) {
        extractDbZips(parameter)
        createPatchList(parameter)
        prepareAssembledZip(parameter)
        deployDbZip(parameter)
    }
    else {
        commonPatchFunctions.log("No DB-Zip(s) to be deployed","assembleAndDeployDb")
    }
}

def deployDbZip(parameter) {
    def dbZipFileName = dbPatchContentFolderName(parameter)
    commonPatchFunctions.log("${dbZipFileName} will be deploy using ${parameter.dbZipDeployTarget}","deployDbZip")
    def deployCmd = "scp -p ${dbZipFileName}.zip ${parameter.dbZipDeployTarget}:Downloads"
    def result = sh ( returnStdout : true, script: deployCmd).trim()
    println "result of ${deployCmd} : ${result}"

    // JHE (23.03.21): This is done here mainly for configuration convenience of SSH Keys
    if(parameter.forProduction) {
        commonPatchFunctions.log("Assemble and deploy has been done for production, result DBZip will be archived","deployDbZip")
        def scpCmd = "scp -p -o \"StrictHostKeyChecking no\" ${dbZipFileName}.zip ${env.ARCHIVE_SERVER_HOST}:${env.ARCHIVE_SERVER_PATH}/${dbZipFileName}_${new Date().format('yyyyMMddHHmmssS')}.zip"
        def scpResult = sh ( returnStdout : true, script: scpCmd).trim()
        println "result of ${scpCmd} : ${scpResult}"
    }
}

def dbPatchContentFolderName(parameter) {
    return "dbPatches${parameter.target}"
}

def prepareAssembledZip(parameter) {
    def dbZipFileName = dbPatchContentFolderName(parameter)
    commonPatchFunctions.log("Creating ZIP file called ","prepareAssembledZip")
    fileOperations ([
            folderCreateOperation(folderPath: dbZipFileName),
            fileCopyOperation(includes: "oracle*/**", targetLocation: dbZipFileName),
            fileCopyOperation(includes: "patch_list", targetLocation: dbZipFileName)
    ])
    zip zipFile: "${dbZipFileName}.zip", dir: dbZipFileName
}

def createPatchList(parameter) {
    def fileContent = parameter.patchNumbers.join(",")
    commonPatchFunctions.log("Creating patch_list file with following content : ${fileContent}","createPatchList")
    fileOperations ([
            fileCreateOperation(fileName: "patch_list", fileContent: fileContent)
    ])
}

def extractDbZips(parameter) {
    int i=1
    commonPatchFunctions.log("Possible ZIP(s) are : ${parameter.dbZipNames}","extractDbZips")
    parameter.patchNumbers.each { patchNumber ->
        commonPatchFunctions.log("Searching ZIP for Patch ${patchNumber}","extractDbZips")
        parameter.dbZipNames.each { dbZipName ->
            if(dbZipName.contains(patchNumber)) {
                commonPatchFunctions.log("Content of ${env.DBZIPS_FILE_PATH}/${dbZipName} zip will be use for final assembled ZIP.","extractDbZips")
                unzip zipFile:"${env.DBZIPS_FILE_PATH}/${dbZipName}"
                fileOperations([
                        folderRenameOperation(source: "oracle", destination: "oracle_${String.format('%04d',i)}_${patchNumber}")
                ])
            }
        }
        i++
    }
}

def put(host,src,dest) {
    commonPatchFunctions.log("Putting ${src} on ${host} into ${dest}","put")
    def remote = getRemoteSSHConnection(host)
    sshPut remote: remote, from: src, into: dest
    commonPatchFunctions.log("DONE - Putting ${src} on ${host} into ${dest}","put")
}

def getRemoteSSHConnection(host) {

    def remote = [:]
    remote.name = "SSH-${host}"
    remote.host = host
    remote.allowAnyHosts = true
//	remote.logLevel = "FINE"

    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshCredentials',
                      usernameVariable: 'SSHUsername', passwordVariable: 'SSHUserpassword']]) {

        remote.user = SSHUsername
        remote.password = SSHUserpassword
    }

    return remote
}

def doAssembleAndDeploy(parameter,revisionClonedPath) {
    commonPatchFunctions.log("Assembling will be done for following packagers : ${parameter.packagers}", "doAssembleAndDeploy")
    parameter.packagers.each{packager ->
        commonPatchFunctions.log("Assembling ${packager.name} started.","doAssembleAndDeploy")
        dir(packager.name) {
            sh "chmod +x ./gradlew"
            def cmd = "./gradlew clean buildPkg deployPkg -Papg.common.repo.gradle.local.repo.from.maven=false -PrevisionRootPath=${revisionClonedPath} -PtargetHost=${packager.targetHost} -PinstallTarget=${parameter.target} ${env.GRADLE_OPTS} --info --stacktrace"
            commonPatchFunctions.runShCommandWithRetry(cmd,5,60)

            // JHE (23.03.21): This is done here mainly for configuration convenience of SSH Keys
            if(parameter.forProduction) {
                commonPatchFunctions.log("Assemble and deploy has been done for production, result Artifacts will be archived","doAssembleAndDeploy")
                def scpCmd = "scp -p -o \"StrictHostKeyChecking no\" build/distributions/*.* ${env.ARCHIVE_SERVER_HOST}:${env.ARCHIVE_SERVER_PATH}"
                def scpResult = sh ( returnStdout : true, script: scpCmd).trim()
                println "result of ${scpCmd} : ${scpResult}"
            }
        }
        commonPatchFunctions.log("Assembling ${packager.name} done!","doAssembleAndDeploy")
    }
}

def logPatchActivity(patchNumberList,target,logText,buildUrl) {
    commonPatchFunctions.log("Logging patch activity for ${patchNumberList} on target ${target} with logText '${logText}' and buildUrl = '${buildUrl}'" ,"logPatchActivity")
    patchNumberList.each{patchNumber ->
        commonPatchFunctions.logPatchActivity(patchNumber, target, "assembleAndDeploy", logText,buildUrl)
    }
}

def checkoutPackagerProjects(packagerProjectList) {
    commonPatchFunctions.log("Following packager will be checked-out from CVS : ${packagerProjectList}", "checkoutPackagerProjects")
    packagerProjectList.each{packager ->
        commonPatchFunctions.coFromBranchCvs(packager.vcsBranch, packager.name)
    }
}
