import javassist.ClassClassPath
import javassist.ClassPool
import javassist.CtClass
import javassist.LoaderClassPath
@Grapes([
        @Grab(group = 'org.eclipse.jgit', module = 'org.eclipse.jgit', version = '5.8.1.202007141445-r'),
        @Grab(group = 'org.javassist', module = 'javassist', version = '3.27.0-GA')
])

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

import java.lang.reflect.Method


// Alt + Enter at Grapes to add context libs for IntelliJ
def traceDiff(String pathDir) {
    final String GIT_SOURCE = pathDir + "/.git";
    Repository repository = new FileRepositoryBuilder().setGitDir(new File(GIT_SOURCE)).build();

    try {
        // Check head with previous one commit
        RevCommit headCommit = getHeadCommit(repository);
        RevCommit diffWith = headCommit.getParent(0);

        def changes = [];
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        DiffFormatter diffFormatter = new DiffFormatter(stdout)
        diffFormatter.setRepository(repository);
        List<DiffEntry> entries = diffFormatter.scan(diffWith, headCommit);

        for (DiffEntry entry : entries) {
            String filename = entry.getNewPath();
            if (filename =~ /.java$|.jsp$/) {
                diffFormatter.format(entry);
                changes += ["file": entry.getNewPath(), "diff": stdout.toString("UTF-8"), "type": entry.getChangeType().toString()]
//                changes += ["file": entry.getNewPath(), "diff": stdout.toString("UTF-8"), "type": entry.getChangeType().toString()]
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


def diffParser(List changes) {
    def diffPattern = /@@(.+?)@@/

    def changeScopes = []
    changes.each { change ->
        if ("MODIFY" == change.type) {
            def diff = change.diff
            println(diff)
            def detachDiff = (diff =~ diffPattern).findAll()*.last()        // [ -7,9 +7,14 ,  -22,13 +27,29 ]

            // Detach changed scopes
            detachDiff.each { number ->
                def splitChanges = number.split(',')
                def indxStart = (splitChanges.first() - "-").toInteger()
                def changeScope = splitChanges.last().toInteger() - 1   // Git always count to unchanged part
                changeScopes += ["startLine": indxStart, "endLine": indxStart + changeScope]
            }
            def lstDiffs = diff.split('\n')

            println(lstDiffs)
        }
    }
    return changeScopes
}

def loadFile(String filePath) {
    def lstLines = new File(filePath).readLines()
    def lineSize = lstLines.size()

    def lstIndexMethods = lstLines.findIndexValues { it =~ /(public|private|protected|enum) / &&
                                                     !it.contains("class") &&
                                                     !it.contains("=")}

    def methods = lstIndexMethods.collect{lstLines[it].split("\\(").first().split(" ").last()}

    def count = 0
    for( lineSize; lineSize >= lstIndexMethods.last(); lineSize--){
        def lineDetect = lstLines[lineSize - 1].trim()
        def isValid = lineDetect.contains("}") &&
                !lineDetect.contains("//") &&
                !lineDetect.contains("/*") &&
                !lineDetect.startsWith("*")
        if(isValid){
            count++
            if(2 == count){
                lstIndexMethods += lineSize - 1
            }
        }
    }

    def exp = lstIndexMethods.size() - 2

    // [22, 55, 64, lstSize]
    def result = []
    for (int i = 0; i <= exp; i++) {
        def line = lstIndexMethods[i + 1]

        for (line; line > lstIndexMethods[i]; line--) {
            def lineDetect = lstLines[line].trim()
            def isValid = lineDetect.contains("}") &&
                    !lineDetect.startsWith("//") &&
                    !lineDetect.startsWith("/*") &&
                    !lineDetect.startsWith("*")

            if (isValid) {
//                if(i == exp){
//                    count++
//                    if(2 == count){
//                        lstIndexMethods += lineSize - 1
//                    }
//                }
                result += ["method": methods[i], "start": lstIndexMethods[i], "end": line]; break
            }
        }
    }
    result
    return result
//    def analyzeList = lstLines[i..j].findAll{!it.contains("//")}
}


//// Execute detection
// Part 1: Detect changes
def sourceGit = H:/Codebase/JGit
def fileDiffs = traceDiff(sourceGit)            // Should detect how many files -> fileDiffs
def filter = diffParser(fileDiffs)              // Detect multiple files && attach class

// Part 2: Detect methods
def filePath = "H:/Codebase/JGit/src/main/java/com/dl/jgit/CommitTrace.java"
def methodDetection = loadFile(filePath)
println()