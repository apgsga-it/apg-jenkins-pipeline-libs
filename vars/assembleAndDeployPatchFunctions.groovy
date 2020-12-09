#!groovy

def assembleAndDeploy(target,parameter) {
    assertParameter(parameter)
    logPatchActivity(parameter.patches,target)
    checkoutPackagerProjects(parameter.gradlePackagerProjectAsVscPath)
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