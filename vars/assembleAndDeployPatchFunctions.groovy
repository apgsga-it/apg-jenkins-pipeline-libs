#!groovy

def assembleAndDeployJavaService(parameter) {
    if(parameter.packagers.size() > 0) {
        checkoutPackagerProjects(parameter.packagers)
        doAssembleAndDeploy(parameter)
    }
    else {
        commonPatchFunctions.log("No Java Services to be assembled!","assembleAndDeployJavaService")
    }
}

def assembleAndDeployDb(parameter) {
    if(parameter.dbZipNames.size() > 0) {
        parameter.dbZipNames.each{dbZipName ->
            // TODO JHE (18.12.2020) : IT-36396 -> to be verified with UGE on 13.01.2021 where we should deploy. Jenkins node? Probably it sas to be a Windows machine
            put("192.168.159.128","${env.DBZIPS_FILE_PATH}/${dbZipName}","/home/apg_install/downloads/dbZips/patch_8001_DEV-CHEI211.zip")
        }
    }
    else {
        commonPatchFunctions.log("No DB-Zip(s) to be deployed","assembleAndDeployJavaService")
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

def doAssembleAndDeploy(parameter) {
    commonPatchFunctions.log("Assembling will be done for following packagers : ${parameter.packagers}", "doAssembleAndDeploy")
    parameter.packagers.each{packager ->
        commonPatchFunctions.log("Assembling ${packager.name} started.","doAssembleAndDeploy")
        dir(packager.name) {
            sh "chmod +x ./gradlew"
            def cmd = "./gradlew clean buildPkg deployPkg -PtargetHost=${packager.targetHost} -PinstallTarget=${parameter.target} -PbuildTyp=CLONED -PbaseVersion=${packager.baseVersion} -PcloneTargetPath=${env.WORKSPACE}/clonedInformation ${env.GRADLE_OPTS} --info --stacktrace"
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
