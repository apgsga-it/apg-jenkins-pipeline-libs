#!groovy

def installDb(parameter) {
    //TODO JHE (18.12.2020) : check with UGE exactly how/where we want to install the DB from
    if(1==1) {
        println "installDB would be done here"
    }
    else {
        commonPatchFunctions.log("No DB-Zip(s) to be deployed","assembleAndDeployJavaService")
    }
}

def installJavaServices(parameters) {
    //TODO JHE (14.12.2020): Eventually the parameters will contain the info if we have to assemble the java part or not
    if(parameter.packagers.size() > 0) {
        checkoutPackagerProjects(parameter.packagers)
        doInstallJavaServices(parameter.packagers,parameter.target)
    }
    else {
        commonPatchFunctions.log("No Java Services to be installed!","installJavaServices")
    }

}

def doInstallJavaServices(packagers,target) {
    commonPatchFunctions.log("Installation will be done for following packagers : ${packagers}", "doInstallJavaServices")
    packagers.each{p ->
        commonPatchFunctions.log("Installing ${p.name} started.","doInstallJavaServices")
        dir(p.name) {
            def cmd = "./gradlew clean installPkg -PtargetHost=${p.targetHost} -PinstallTarget=${target} -PbaseVersion=${p.baseVersion} -Dgradle.user.home=${env.GRADLE_USER_HOME_PATH} --info --stacktrace"
            def result = sh ( returnStdout : true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
        }

        commonPatchFunctions.log("Installing ${p.name} done!","doInstallJavaServices")
    }
}

def checkoutPackagerProjects(packagers) {
    commonPatchFunctions.log("Following packager will be checked-out from CVS : ${packagers}", "checkoutPackagerProjects")
    packagers.each{p ->
        commonPatchFunctions.coFromBranchCvs(p.vcsBranch, p.name)
    }
}

// TODO JHE (15.12.2020): Move this into commonPatchFunctions
def logPatchActivity(patchNumberList,target,logText) {
    commonPatchFunctions.log("Logging patch activity for ${patchNumberList}","logPatchActivity")
    patchNumberList.each{patchNumber ->
        commonPatchFunctions.logPatchActivity(patchNumber, target, "install", logText)
    }
}