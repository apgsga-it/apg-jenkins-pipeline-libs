import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic

def readPatchFile(patchFilePath) {
    def patchFile = new File(patchFilePath)
    def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
    patchConfig.patchFilePath = patchFilePath
    patchConfig
}

def loadTargetsMap() {
    def targetSystemMap = [:]
    readTargetSytemMappingFile().stageMappings.each( { target ->
        targetSystemMap.put(target.name, [envName:target.name,targetName:target.target])
    })
    log(targetSystemMap,"loadTargetsMap")
    targetSystemMap
}


def readTargetSytemMappingFile() {
    def configFileLocation = env.PATCH_SERVICE_COMMON_CONFIG ? env.PATCH_SERVICE_COMMON_CONFIG	: "/etc/opt/apg-patch-common/TargetSystemMappings.json"
    def targetSystemFile = new File(configFileLocation)
    assert targetSystemFile.exists()
    return new JsonSlurper().parseText(targetSystemFile.text)
}

def initPatchConfig(patchConfig, params) {
    patchConfig.cvsroot = env.CVS_ROOT
    patchConfig.patchFilePath = params.PARAMETER
    patchConfig.redo = params.RESTART.equals("TRUE")
}

def redoToState(patchConfig) {
    if (!patchConfig.redo) {
        patchConfig.redoToState = ""
        return
    }
    patchConfig.redoToState = patchConfig.targetToState
}

def serviceInstallationNodeLabel(target,serviceName) {
    target.nodes.each{node ->
        if(node.serviceName.equalsIgnoreCase(serviceName)) {
            label = node.label
        }
    }
    assert label?.trim() : "No label found for ${serviceName}"
    return label
}

def stage(target,toState,patchConfig,task, Closure callBack) {
    log("target: ${target}, toState: ${toState}, task: ${task} ","stage")
    patchConfig.currentPipelineTask = "${task}"
    logPatch(patchConfig,"started")
    def targetSystemsMap = loadTargetsMap()
    def targetName= targetSystemsMap.get(target.envName)
    patchConfig.targetToState = mapToState(target,toState)
    log("patchConfig.targetToState: ${patchConfig.targetToState}","stage")
    log("patchConfig.redoToState: ${patchConfig.redoToState}","stage")
    def skip = patchConfig.redo &&
            (!(patchConfig.redoToState.toString().equals(patchConfig.targetToState.toString()) && patchConfig.lastPipelineTask.toString().equals(task.toString())))
    def nop = !skip && patchConfig.mavenArtifacts.empty && patchConfig.dbObjects.empty && !patchConfig.installJadasAndGui && !patchConfig.installDockerServices && !["Approve","Notification"].contains(task)
    log("skip = ${skip}","stage")
    log("nop  = ${nop}","stage")
    def stageText = "${target.envName} (${target.targetName}) ${toState} ${task} "  + (skip ? "(Skipped)" : (nop ? "(Nop)" : "") )
    def logText
    stage(stageText) {
        if (!skip) {
            log("Not skipping","stage")
            // Save before Stage
            if (targetName != null) {
                savePatchConfigState(patchConfig)
            }
            if (!nop) {
                callBack(patchConfig)
                patchConfig.lastPipelineTask = task
            }
            if (patchConfig.redoToState.toString().equals(patchConfig.targetToState.toString()) && patchConfig.lastPipelineTask.toString().equals(task.toString())) {
                patchConfig.redo = false
            }
            if (targetName != null) {
                savePatchConfigState(patchConfig)
            }
            // JHE: instead of "nop", what could we better write for the developer?
            logText =  nop ? "nop" : "done"
        } else {
            log("skipping","stage")
            logText = "skipped"
        }
    }
    logPatch(patchConfig, logText)
}



def savePatchConfigState(patchConfig) {
    node {
        log("Saving Patchconfig State ${patchConfig.patchNummer}","savePatchConfigState")
        def patchFileName = "Patch${patchConfig.patchNummer}.json"
        writeFile file: patchFileName , text: new JsonBuilder(patchConfig).toPrettyString()
        def cmd = "/opt/apg-patch-cli/bin/apscli.sh -s ${patchFileName}"
        log("Executeing ${cmd}","savePatchConfigState")
        sh "${cmd}"
        log("Executeing ${cmd} done.","savePatchConfigState")
    }
}

// Used in order to have Datetime info in our pipelines
def log(msg,caller) {
    def dt = "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}"
    def logMsg = caller != null ? "(${caller}) ${dt}: ${msg}" : "${dt}: ${msg}"
    echo logMsg
}

// Used in order to have Datetime info in our pipelines
def log(msg) {
    log(msg,null)
}


private def logPatch(def patchConfig, def logText) {
    node {
        patchConfig.logText = logText
        def patchFileName = "PatchLog${patchConfig.patchNummer}.json"
        writeFile file: patchFileName , text: new JsonBuilder(patchConfig).toPrettyString()
        def cmd = "/opt/apg-patch-cli/bin/apscli.sh -log ${patchFileName}"
        log("Executeing ${cmd}","logPatch")
        sh "${cmd}"
        log("Executeing ${cmd} done.","logPatch")
    }
}

