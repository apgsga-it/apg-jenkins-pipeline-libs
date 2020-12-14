#!groovy

def assembleAndDeployJavaService(target,parameter) {
    //TODO JHE (14.12.2020): Eventually the parameters will contain the info if we have to assemble the java part or not
    if(parameter.gradlePackagerProjectAsVscPath.size() > 0) {
        checkoutPackagerProjects(parameter.gradlePackagerProjectAsVscPath)
        doAssembleAndDeploy(parameter.gradlePackagerProjectAsVscPath)
    }
    else {
        commonPatchFunctions.log("No Java Services to be assembled!","assembleAndDeployJavaService")
    }
}

def assembleAndDeployDb(target,parameter) {

    //TODO JHE : need to get a parameter saying if a patch has a db-zip to be deployed or not
    if(1==1) {
        //TODO JHE : this will obviously be replaced, getting values from parameters, etc ..., here to test the copy of a zip
        //TODO JHE : Also, we need to iterate over patches, probably
        put("192.168.159.128","/var/jenkins/dbZips/patch_8001_DEV-CHEI211.zip","/home/apg_install/downloads/dbZips")
    }
    else {
        commonPatchFunctions.log("No DB-Zip(s) to be deployed","assembleAndDeployJavaService")
    }
}

def put(host,src,dest) {
    patchfunctions.log("Putting ${src} on ${host} into ${dest}","put")
    def remote = getRemoteSSHConnection(host)
    sshPut remote: remote, from: src, into: dest
    patchfunctions.log("DONE - Putting ${src} on ${host} into ${dest}","put")
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

def doAssembleAndDeploy(packagerProjectList) {
    commonPatchFunctions.log("Assembling will be done for following packagers : ${packagerProjectList}", "checkoutPackagerProjects")
    packagerProjectList.each{packager ->
        commonPatchFunctions.log("Assembling ${packager} started.","assemble")

        dir(packager) {
            //TODO JHE (11.12.2020) : Get all parameter values from parameters passed within JSON params .... First waiting on IT-36505 to be done
            def cmd = "./gradlew clean buildPkg deployPkg -PtargetHost=192.168.159.128 -PinstallTarget=dev-chei211 -PbuildTyp=CLONED -PbaseVersion=1.0.0-DEV-ADMIN_UIMIG -PcloneTargetPath=${env.WORKSPACE}/clonedInformation -Dgradle.user.home=${env.GRADLE_USER_HOME_PATH} --info --stacktrace"
            def result = sh ( returnStdout : true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
        }

        commonPatchFunctions.log("Assembling ${packager} done!","assemble")
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
        // TODO JHE (09.12.2020) : Get "head" from service metadata or pass it along with parameters, waiting on IT-36505
        commonPatchFunctions.coFromBranchCvs("HEAD", packager)
    }
}
