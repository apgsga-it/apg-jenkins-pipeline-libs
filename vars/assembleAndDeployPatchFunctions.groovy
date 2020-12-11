#!groovy

def assembleAndDeploy(target,parameter) {
    assertParameter(parameter)
    logPatchActivity(parameter.patches,target)
    checkoutPackagerProjects(parameter.gradlePackagerProjectAsVscPath)
    assemble(parameter.gradlePackagerProjectAsVscPath)
}

def assemble(packagerProjectList) {
    commonPatchFunctions.log("Assembling will be done for following packagers : ${packagerProjectList}", "checkoutPackagerProjects")
    packagerProjectList.each{packager ->
        commonPatchFunctions.log("Assembling ${packager} started.","assemble")

        dir(packager) {
            //def cmd = "./gradlew clean buildPkg -Dgradle.user.home=${env.GRADLE_USER_HOME_PATH} --stacktrace --info"
            def cmd = "./gradlew clean buildPkg -PtargetHost=dev-chei211 -PinstallTarget=dev-chei211 -PbuildTyp=CLONED -PbaseVersion=1.0 -PcloneTargetPath=clonedInformation -Dgradle.user.home=${env.GRADLE_USER_HOME_PATH} --info --stacktrace"
            def result = sh ( returnStdout : true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
        }

        commonPatchFunctions.log("Assembling ${packager} done!","assemble")
    }
}

def logPatchActivity(patchNumberList,target) {
    commonPatchFunctions.log("Logging patch activity for ${patchNumberList}","logPatchActivity")
    patchNumberList.each{patchNumber ->
        commonPatchFunctions.logPatchActivity(patchNumber, target, "assembleAndDeploy", "started")
    }
}

def checkoutPackagerProjects(packagerProjectList) {
    commonPatchFunctions.log("Following packager will be checked-out from CVS : ${packagerProjectList}", "checkoutPackagerProjects")
    packagerProjectList.each{packager ->
        // TODO JHE (09.12.2020) : Get "head" from service metadata or pass it along with parameters
        commonPatchFunctions.coFromBranchCvs("HEAD", packager)
    }
}

def assertParameter(parameter) {
    commonPatchFunctions.log("TODO JHE , assert that required parameter are present","assertParameter")
}