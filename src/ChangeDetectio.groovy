@Grapes(
        @Grab(group = 'org.eclipse.jgit', module = 'org.eclipse.jgit', version = '5.8.1.202007141445-r')
)

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

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
            if (filename.endsWith(".java")) {
                diffFormatter.format(entry);
                changes += ["file": entry.getNewPath(), "diff": stdout.toString("UTF-8"), "type": entry.getChangeType().toString()]
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


// Execute detection

def diffs = traceDiff("H:/Codebase/JGit")
println(diffs.diff)