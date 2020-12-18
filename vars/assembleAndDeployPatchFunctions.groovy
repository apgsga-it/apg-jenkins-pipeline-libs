#!groovy

def assembleAndDeployJavaService(parameter) {
    if(parameter.packagers.size() > 0) {
        checkoutPackagerProjects(parameter.packagers)
        doAssembleAndDeploy(parameter.packagers)
    }
    else {
        commonPatchFunctions.log("No Java Services to be assembled!","assembleAndDeployJavaService")
    }
}

def assembleAndDeployDb(parameter) {

    //TODO JHE : need to get a parameter saying if a patch has a db-zip to be deployed or not
    if(1==1) {
        //TODO JHE : this will obviously be replaced, getting values from parameters, etc ..., here to test the copy of a zip
        //TODO JHE : Also, we need to iterate over patches, probably
        put("192.168.159.128","/var/jenkins/dbZips/patch_8001_DEV-CHEI211.zip","/home/apg_install/downloads/dbZips/patch_8001_DEV-CHEI211.zip")
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

def doAssembleAndDeploy(packagers) {
    commonPatchFunctions.log("Assembling will be done for following packagers : ${packagers}", "doAssembleAndDeploy")
    packagers.each{packager ->
        commonPatchFunctions.log("Assembling ${packager.name} started.","doAssembleAndDeploy")

        dir(packager.name) {
            //TODO JHE (11.12.2020) : Get all parameter values from parameters passed within JSON params .... First waiting on IT-36505 to be done
            def cmd = "./gradlew clean buildPkg deployPkg -PtargetHost=192.168.159.128 -PinstallTarget=dev-chei211 -PbuildTyp=CLONED -PbaseVersion=1.0.0-DEV-ADMIN_UIMIG -PcloneTargetPath=${env.WORKSPACE}/clonedInformation -Dgradle.user.home=${env.GRADLE_USER_HOME_PATH} --info --stacktrace"
            def result = sh ( returnStdout : true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
        }

        commonPatchFunctions.log("Assembling ${packager.name} done!","doAssembleAndDeploy")
    }
}

// TODO JHE (15.12.2020): Move this into commonPatchFunctions
def logPatchActivity(patchNumberList,target,logText) {
    commonPatchFunctions.log("Logging patch activity for ${patchNumberList}","logPatchActivity")
    patchNumberList.each{patchNumber ->
        commonPatchFunctions.logPatchActivity(patchNumber, target, "assembleAndDeploy", logText)
    }
}

def checkoutPackagerProjects(packagerProjectList) {
    commonPatchFunctions.log("Following packager will be checked-out from CVS : ${packagerProjectList}", "checkoutPackagerProjects")
    packagerProjectList.each{packager ->
        // TODO JHE (09.12.2020) : Get "head" from service metadata or pass it along with parameters, waiting on IT-36505
        commonPatchFunctions.coFromBranchCvs(packager.vcsBranch, packager.name)
    }
}
