#!groovy

def installDb(target,parameter) {
    //TODO JHE : need to get a parameter saying if a patch has a db-zip to be installed or not
    if(1==1) {
        //TODO JHE (15.12.2020) : check with UGE exactly how/where we want to install the DB from
        println "installDB would be done here"
    }
    else {
        commonPatchFunctions.log("No DB-Zip(s) to be deployed","assembleAndDeployJavaService")
    }
}

def installJavaServices(target,parameter) {
    //TODO JHE (14.12.2020): Eventually the parameters will contain the info if we have to assemble the java part or not
    if(parameter.gradlePackagerProjectAsVscPath.size() > 0) {
        checkoutPackagerProjects(parameter.gradlePackagerProjectAsVscPath)
        doInstallJavaServices(parameter.gradlePackagerProjectAsVscPath)
    }
    else {
        commonPatchFunctions.log("No Java Services to be installed!","installJavaServices")
    }

}

def doInstallJavaServices(packagerProjectList) {
    commonPatchFunctions.log("Installation will be done for following packagers : ${packagerProjectList}", "doInstallJavaServices")
    packagerProjectList.each{packager ->
        commonPatchFunctions.log("Installing ${packager} started.","doInstallJavaServices")
        dir(packager) {
            //TODO JHE (11.12.2020) : Get all parameter values from parameters passed within JSON params .... First waiting on IT-36505 to be done
            def cmd = "./gradlew clean installPkg -PtargetHost=192.168.159.128 -PinstallTarget=dev-chei211 -PbaseVersion=1.0.0-DEV-ADMIN_UIMIG -Dgradle.user.home=${env.GRADLE_USER_HOME_PATH} --info --stacktrace"
            def result = sh ( returnStdout : true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
        }

        commonPatchFunctions.log("Installing ${packager} done!","doInstallJavaServices")
    }
}

def checkoutPackagerProjects(packagerProjectList) {
    commonPatchFunctions.log("Following packager will be checked-out from CVS : ${packagerProjectList}", "checkoutPackagerProjects")
    packagerProjectList.each{packager ->
        // TODO JHE (09.12.2020) : Get "head" from service metadata or pass it along with parameters, waiting on IT-36505
        commonPatchFunctions.coFromBranchCvs("HEAD", packager)
    }
}

// TODO JHE (15.12.2020): Move this into commonPatchFunctions
def logPatchActivity(patchNumberList,target,logText) {
    commonPatchFunctions.log("Logging patch activity for ${patchNumberList}","logPatchActivity")
    patchNumberList.each{patchNumber ->
        commonPatchFunctions.logPatchActivity(patchNumber, target, "install", logText)
    }
}