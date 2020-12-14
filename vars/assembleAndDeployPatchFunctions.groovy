#!groovy

def assembleAndDeploy(target,parameter) {
    logPatchActivity(parameter.patches,target,"started")
    checkoutPackagerProjects(parameter.gradlePackagerProjectAsVscPath)
    doAssembleAndDeploy(parameter.gradlePackagerProjectAsVscPath)
    logPatchActivity(parameter.patches,target,"done")
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
