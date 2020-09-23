@Grapes([
        @Grab(group = 'org.eclipse.jgit', module = 'org.eclipse.jgit', version = '5.8.1.202007141445-r'),
        @Grab(group = 'org.slf4j', module = 'slf4j-nop', version = '1.7.25', scope = 'test'),
        @Grab(group = 'org.mongodb', module = 'mongo-java-driver', version = '3.12.7')
])

//@GrabConfig(systemClassLoader=true)
//@Grab(group='mysql', module='mysql-connector-java', version='5.1.6')

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter;
import com.mongodb.*;

// Alt + Enter at Grapes to add context libs for IntelliJ
/* --------------- ------- Main Git interaction ------- --------------*/

def traceDiff(String pathDir) {
    final String GIT_SOURCE = pathDir + "/.git";
    Repository repository = new FileRepositoryBuilder().setGitDir(new File(GIT_SOURCE)).build();

    try {
        // Check head with previous one commit
        RevCommit headCommit = getHeadCommit(repository)
        RevCommit diffWith = headCommit.getParent(0)

        def changes = []
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        DiffFormatter diffFormatter = new DiffFormatter(stdout)
        diffFormatter.setRepository(repository);

        // For commit
        List<DiffEntry> entries = diffFormatter.scan(diffWith, headCommit)
        for (DiffEntry entry : entries) {
            String filename = entry.getNewPath();
            if (filename =~ /.java$|.jsp$/) {
                diffFormatter.format(entry);
                changes += ["file": entry.getNewPath(), "diff": stdout.toString("UTF-8"), "type": entry.getChangeType().toString()]
                stdout.reset()
            }
        }
        return changes
    } catch (e) {
        println("Source-code should have more than one commit. Looks like there is only ONE commit or else. Please check it")
    }
}


def RevCommit getHeadCommit(Repository repository) {
    try {
        Git git = new Git(repository)
        Iterable<RevCommit> history = git.log().setMaxCount(1).call();
        return history.iterator().next();
    } catch (e) {
    }
}


/* --------------- ------- Handle logic  operation ------- --------------*/

def diffParser(List diffFiles) {
    def diffPattern = /@@(.+?)@@/

    def results = []
    diffFiles.each { change ->
        // Only interact with file changed (not added new file)
        if ("MODIFY" == change.type) {
            def diff = change.diff
            def lstDiffs = diff.split('\n')

            def changedScopes = []
            def detachDiff = (diff =~ diffPattern).findAll()*.last()        // >> [ -7,9 +7,14 ,  -22,13 +27,29 ]

            // Detach changed scopes
            detachDiff.each { number ->
                def prevChanges = (number.trim().split(' ').first() - '-').split(',')           // 7,9
                def curChanges = (number.trim().split(' ').last() - '+').split(',')             // 7,14
                def startIndex = curChanges.first().toInteger() - 1     // Deduct for the current @@ .. @@ line
                def changedScope = curChanges.last().toInteger()

                // Handle lines changed
                def detect = lstDiffs.findIndexValues { it.toString().contains(number) }.first()
                def detectArea = detect + changedScope

                def changedLines = []
                if (prevChanges.first() == curChanges.first()
                        && prevChanges.last() > curChanges.last()) {
                    changedLines = lstDiffs[detect..detectArea].findIndexValues { it.trim().startsWith("-") && (it.trim() - "-") != "" }*.plus(startIndex - 1)
                } else {
                    changedLines = lstDiffs[detect..detectArea].findAll { !it.toString().trim().startsWith("-") }
                            .findIndexValues { it.trim().startsWith("+") }*.plus(startIndex)
                }
                changedScopes += ["scope": number, "lines": changedLines]

            }

            results += ["file": change.file, "change": changedScopes]
        }
    }
    return results
}


def methodDetection(String filePath) {
    def lstLines = new File(filePath).readLines()
    def lineSize = lstLines.size()

    def lstIndexMethods = lstLines.findIndexValues {
        it =~ /(public|private|protected|enum) / &&
                !it.contains("class") &&
                !it.contains("=")
    }

    def methods = lstIndexMethods.collect { lstLines[it].split("\\(").first().split(" ").last() }

    def count = 0
    for (lineSize; lineSize >= lstIndexMethods.last(); lineSize--) {
        def lineDetect = lstLines[lineSize - 1].trim()
        def isValid = lineDetect.contains("}") &&
                !lineDetect.startsWith("/") &&
                !lineDetect.startsWith("*")
        if (isValid) {
            count++
            if (2 == count) {
                lstIndexMethods += lineSize - 1
            }
        }
    }

    // [22, 55, 64, lstSize]
    def result = []
    for (int i = 0; i <= lstIndexMethods.size() - 2; i++) {
        def line = lstIndexMethods[i + 1]

        for (line; line > lstIndexMethods[i]; line--) {
            def lineDetect = lstLines[line].trim()
            def isValid = lineDetect.contains("}") &&
                    !lineDetect.startsWith("/") &&
                    !lineDetect.startsWith("*")

            if (isValid) {
                result += ["name": methods[i], "start": lstIndexMethods[i] + 1, "end": line + 1]; break
            }
        }
    }
    return result
}


//def fileInfo(def filter, def sourcePath = "") {
def fileInfo(def files, def sourceDir = "") {
    def methodInfos = []
    files.each { file ->
        def filePath = sourceDir + file
        methodInfos += ["file": file, "method": methodDetection(filePath)]
    }
    return methodInfos.unique()
}


def impactAnalysis(def filter, def sourceDir = "") {
    // methods: [[
    //              file:file, method:[
    //                                  ["name": method, "start": number, "end": number],
    //                                  ["name": method, "start": number, "end": number]
    //                                ]
    //          ]]
    //
    // filter: [
    //              [file": file, change: ["scope": number, "lines": [changedLines]],
    //                                    ["scope": number, "lines": [changedLines]]],
    //              [file": file, change: ["scope": number, "lines": changedLines]]
    //          ]

    // Get method info from diff
    def infoDetected = fileInfo(filter.file, sourceDir)

    // Detect changed line in file >> method
    def results = []
    filter.each { item ->
        def file = new File(item.file)
        def fileName = file.parentFile.toURI().relativize(file.toURI()).getPath()
        def className = fileName - ".java"

        def infoMethods = infoDetected.find { it.file == item.file }?.method

        def lines = item.change.lines.flatten()
        lines.each { num ->
            infoMethods.each { m ->
                if (num in m.start..m.end) {
                    results += "${className}.${m.name}"
                }
            }
        }
    }
    return results
}


def testCaseImpacted(def resultAnalysis) {
    def CONNECTION = "192.168.56.120"
    def PORT = 27017
    def dbClient = new MongoClient(CONNECTION, PORT)
    DB db = dbClient.getDB("FlashHatch")
    DBCollection col = db.getCollection("TestCases");

    def results = []
    BasicDBObject query = new BasicDBObject()
    resultAnalysis.each { m ->
        query.put("coverName", new BasicDBObject("\$in", [m.toString()]))
        results += col.find(query).toArray().collect { it.tc }
    }
    def tcIDs = results.flatten().unique().findAll{it.toString().contains("ID")}
    return tcIDs.join(",");
}


//// Execute detection
// Part 1: Detect changes
//def sourceGit = "H:/Codebase/Jersey_Spring/"
def sourceGit = "./"
def fileDiffs = traceDiff(sourceGit)            // Should detect how many files -> fileDiffs
def filter = diffParser(fileDiffs)              // Detect multiple files && attach class

// Part 2: Detect method
def methodImpacted = impactAnalysis(filter, sourceGit).unique()

// Part 3: Query databse
def tcIDs = testCaseImpacted(methodImpacted)


// Output to Jenkins catch-up
println(tcIDs)


// USE by CLI:  groovy impactDetection.groovy