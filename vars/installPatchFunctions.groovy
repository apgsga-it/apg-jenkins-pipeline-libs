#!groovy

import javax.mail.Message
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

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

def installDockerServices(parameters) {
    if(parameters.installDockerServices) {
        commonPatchFunctions.log("Docker service Installation started","installDockerServices")
        def installCmd = "${parameters.pathToDockerInstallScript} ${parameters.target} ${parameters.patchNumbers.join(",")}"
        def result = sh ( returnStdout : true, script: installCmd).trim()
        println "result of ${installCmd} : ${result}"
    }
    else {
        commonPatchFunctions.log("No Docker Services to be installed!","installDockerServices")
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

def installationPostProcess(parameters) {
    if(parameters.isProductionInstallation) {
        parameters.patchNumbers.each { patchNumber ->
            try {
                mergeDbObjectOnHead(patchNumber, parameters)
            }
            catch (err) {
                commonPatchFunctions.log("Error while merging DB Object on head : ${err}", "installationPostProcess")
                def subject = "Error during post process Job for following patch:  ${patchNumber}"
                def body = "DB Object(s) couldn't be merged on productive branch (branch name -> 'prod') for Patch ${patchNumber}, please resolve the problem manually. "
                body += "Note that this problem didn't put the pipeline in error, that means Patch ${patchNumber} has been installed in production. "
                body += System.getProperty("line.separator")
                body += System.getProperty("line.separator")
                body += "Error was: ${err}"
                body += System.getProperty("line.separator")
                body += System.getProperty("line.separator")
                body += "For any question, please contact Stefan Brandenberger, Ulrich Genner or Julien Helbling. "
                body += "Patch Configuration was: "
                body += System.getProperty("line.separator")
                body += System.getProperty("line.separator")
                sendMail(subject, body, env.PIPELINE_ERROR_MAIL_TO)
            }
        }
    }
}

def sendMail(def subject, def body, def to) {
    Properties properties = System.getProperties()
    properties.setProperty("mail.smtp.host", env.SMTP_HOST)
    properties.setProperty("mail.smtp.port", env.SMTP_PORT)
    Session session = Session.getDefaultInstance(properties)
    try{
        MimeMessage msg = new MimeMessage(session)
        msg.setFrom(new InternetAddress(env.PIPELINE_MAIL_FROM))
        to.split(',').each(){ item -> msg.addRecipient(Message.RecipientType.TO,new InternetAddress(item))}
        msg.setSubject("${env.PIPELINE_MAIL_ENV} - ${subject}")
        msg.setText(body)
        Transport.send(msg)
    } catch(RuntimeException e) {
        println e.getMessage()
    }
}

def mergeDbObjectOnHead(patchNumber,patchParameter) {
    /*
     * JHE (22.05.2018): Within this function, we're calling a "cvs" command from shell. This is not ideal, and at best we should use a similar SCM Command as within
     * 					 coFromTagcvs method. So far I didn't find an equivalent build-in function allowing to do a merge.
     *
     */

    def cvsRoot = env.CVS_ROOT

    def dbPatchTag = patchParameter.installDbObjectsInfos."${patchNumber}".dbPatchTag
    def dbPatchBranch = patchParameter.installDbObjectsInfos."${patchNumber}".dbPatchBranch
    def dbProdBranch = "prod"

    def dbTagBeforeMerge = "${dbProdBranch}_merge_${dbPatchBranch}_before"
    def dbTagAfterMerge = "${dbProdBranch}_merge_${dbPatchBranch}_after"

    commonPatchFunctions.log("Patch ${patchNumber} being merged to production branch", "mergeDbObjectOnHead")
    patchParameter.installDbObjectsInfos."${patchNumber}".dbObjectsModuleNames.each { dbModule ->
        commonPatchFunctions.log("- module ${dbModule} tag ${dbPatchTag} being merged to branch ${dbProdBranch}", "mergeDbObjectOnHead")
        sh "cvs -d${cvsRoot} co -r${dbProdBranch} ${dbModule}"
        commonPatchFunctions.log("... ${dbModule} checked out from branch ${dbProdBranch}", "mergeDbObjectOnHead")
        sh "cvs -d${cvsRoot} tag -F ${dbTagBeforeMerge} ${dbModule}"
        commonPatchFunctions.log("... ${dbModule} tagged ${dbTagBeforeMerge}", "mergeDbObjectOnHead")
        sh "cvs -d${cvsRoot} up -d -j ${dbPatchTag} ${dbModule}"
        commonPatchFunctions.log("... ${dbModule} tag ${dbPatchTag} merged to branch ${dbProdBranch}", "mergeDbObjectOnHead")
        try {
            sh "cvs -d${cvsRoot} commit -m 'merge ${dbPatchTag} to branch ${dbProdBranch}' ${dbModule}"
        } catch (Exception mergeEx) {
            commonPatchFunctions.log("... ${dbModule} tag ${dbPatchTag} had merge conflicts for branch ${dbProdBranch} -> forcing contents of tag ${dbPatchTag}", "mergeDbObjectOnHead")
            def tmpFolderDir = "cvsExportTemp_${patchNumber}"
            sh "mkdir -p ${tmpFolderDir}"
            sh "cd ${tmpFolderDir} && cvs -d${cvsRoot} export -r ${dbPatchTag} ${dbModule}"
            sh "cd ${tmpFolderDir} && find * -type f -exec cp -f -p {} ../{} \\;"
            sh "rm -Rf ${tmpFolderDir}"
            sh "cvs -d${cvsRoot} commit -m 'merge ${dbPatchTag} to branch'"
        }
        commonPatchFunctions.log("... ${dbModule} commited", "mergeDbObjectOnHead")
        sh "cvs -d${cvsRoot} tag -F ${dbTagAfterMerge} ${dbModule}"
        commonPatchFunctions.log("... ${dbModule} tagged ${dbTagAfterMerge}", "mergeDbObjectOnHead")
        commonPatchFunctions.log("- module ${dbModule} tag ${dbPatchTag} merged to branch ${dbProdBranch}", "mergeDbObjectOnHead")
    }
    commonPatchFunctions.log("Patch ${patchNumber} merged to production branch", "mergeDbObjectOnHead")
}