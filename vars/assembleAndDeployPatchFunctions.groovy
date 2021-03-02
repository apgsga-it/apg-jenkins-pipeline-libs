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
        int i=1
        commonPatchFunctions.log("Possible ZIP(s) are : ${parameter.dbZipNames}","assembleAndDeployDb")
        parameter.patchNumbers.each { patchNumber ->
            commonPatchFunctions.log("Searching ZIP for Patch ${patchNumber}","assembleAndDeployDb")
            parameter.dbZipNames.each { dbZipName ->
                if(dbZipName.contains(patchNumber)) {
                    commonPatchFunctions.log("Content of ${env.DBZIPS_FILE_PATH}/${dbZipName} zip will be use for final assembled ZIP.","assembleAndDeployDb")
                    unzip zipFile:"${env.DBZIPS_FILE_PATH}/${dbZipName}"
                    fileOperations([
                            folderRenameOperation(source: "oracle", destination: "oracle_${String.format('%04d',i)}")
                    ])
                }
            }
            i++
        }

        fileOperations ([
                fileCreateOperation(fileName: "patch_list", fileContent: parameter.patchNumbers.join(","))
        ])
    }
    else {
        commonPatchFunctions.log("No DB-Zip(s) to be deployed","assembleAndDeployDb")
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
            def result = sh ( returnStdout : true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
        }
        commonPatchFunctions.log("Assembling ${packager.name} done!","doAssembleAndDeploy")
    }
}

def logPatchActivity(patchNumberList,target,logText) {
    commonPatchFunctions.log("Logging patch activity for ${patchNumberList}","logPatchActivity")
    patchNumberList.each{patchNumber ->
        commonPatchFunctions.logPatchActivity(patchNumber, target, "assembleAndDeploy", logText)
    }
}

def checkoutPackagerProjects(packagerProjectList) {
    commonPatchFunctions.log("Following packager will be checked-out from CVS : ${packagerProjectList}", "checkoutPackagerProjects")
    packagerProjectList.each{packager ->
        commonPatchFunctions.coFromBranchCvs(packager.vcsBranch, packager.name)
    }
}
