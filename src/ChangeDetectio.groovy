@Grapes([
        @Grab(group = 'org.eclipse.jgit', module = 'org.eclipse.jgit', version = '5.8.1.202007141445-r'),
        @Grab(group = 'org.javassist', module = 'javassist', version = '3.27.0-GA')
])

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

// Alt + Enter at Grapes to add context libs for IntelliJ
/* --------------- ------- Main Git interaction ------- --------------*/

def traceDiff(String pathDir) {
    final String GIT_SOURCE = pathDir + "/.git";
    Repository repository = new FileRepositoryBuilder().setGitDir(new File(GIT_SOURCE)).build();

    try {
        // Check head with previous one commit
        RevCommit headCommit = getHeadCommit(repository);
        RevCommit diffWith = headCommit.getParent(0);

        // and using commit's tree find the path
//        def headTree = headCommit.getTree();
//        def prevTree = diffWith.getTree();
//        System.out.println("Having tree: " + tree);


        //----------
        def changes = [];
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        DiffFormatter diffFormatter = new DiffFormatter(stdout)
        diffFormatter.setRepository(repository);

        // For commit
        List<DiffEntry> entries = diffFormatter.scan(diffWith, headCommit);

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

def diffParser(List changes) {
    def diffPattern = /@@(.+?)@@/

    def changedScopes = []
    changes.each { change ->
        // Only interact with file changed (not added new file)
        if ("MODIFY" == change.type) {
            def diff = change.diff
            def lstDiffs = diff.split('\n')

            def detachDiff = (diff =~ diffPattern).findAll()*.last()        // >> [ -7,9 +7,14 ,  -22,13 +27,29 ]

            // Detach changed scopes
            detachDiff.each { number ->
//                def prevChanges = (number.trim().split(' ').first() - '-').split(',')
                def curChanges = (number.trim().split(' ').last() - '+').split(',')
                def startIndex = curChanges.first().toInteger() - 1     // Deduct for the current @@ .. @@ line
                def changedScope = curChanges.last().toInteger()

                // Handle lines changed
                def detect = lstDiffs.findIndexValues { it.toString().contains(number) }.first()
                def detectArea = detect + changedScope
                def changedLines = lstDiffs[detect..detectArea].findAll { !it.toString().trim().startsWith("-") }
                        .findIndexValues { it.trim().startsWith("+") }*.plus(startIndex)
                changedScopes += ["file": change.file, "scope": number, "lines": changedLines]
            }
        }
    }
    return changedScopes
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
                // Plus 1 for counting nature from 1
            }
        }
    }
    return result
}


//// Execute detection
// Part 1: Detect changes
def sourceGit = "H:/Codebase/JGit/"
def fileDiffs = traceDiff(sourceGit)            // Should detect how many files -> fileDiffs
def filter = diffParser(fileDiffs)              // Detect multiple files && attach class

// Part 2: Detect methods
//def filePath = sourceGit + "/src/main/java/com/dl/jgit/CommitTrace.java"

def methods = []
filter.each{ item ->
    def filePath = sourceGit + item.file
    methods += ["file": item.file, "method": methodDetection(filePath)]
}

println()