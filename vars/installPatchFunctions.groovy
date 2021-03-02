#!groovy

def installDb(parameter) {
    if(parameter.installDbPatch) {
        commonPatchFunctions.log("DB Installation started","installDb")
        def dbZipFileName = "dbPatches${parameter.target}.zip"
        def installCmd = "ssh ${parameter.dbZipInstallFrom} \"%CM_WINPROC_ROOT%\\install_patch.bat ${dbZipFileName} ${parameter.target}\""
        def result = sh ( returnStdout : true, script: installCmd).trim()
        println "result of ${installCmd} : ${result}"
    }
    else {
        commonPatchFunctions.log("No DB-Zip(s) to be installed","installDb")
    }
}

def installJavaServices(parameters) {
    if(parameters.packagers.size() > 0) {
        checkoutPackagerProjects(parameters.packagers)
        doInstallJavaServices(parameters.packagers,parameters.target)
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
            sh "chmod +x ./gradlew"
            def cmd = "./gradlew clean installPkg -PtargetHost=${p.targetHost} -PinstallTarget=${target}   ${env.GRADLE_OPTS} --info --stacktrace"
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

def logPatchActivity(patchNumberList,target,logText) {
    commonPatchFunctions.log("Logging patch activity for ${patchNumberList}","logPatchActivity")
    patchNumberList.each{patchNumber ->
        commonPatchFunctions.logPatchActivity(patchNumber, target, "install", logText)
    }
}