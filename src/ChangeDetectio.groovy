import javassist.ClassClassPath
import javassist.ClassPool
import javassist.CtClass
import javassist.LoaderClassPath
@Grapes([
        @Grab(group = 'org.eclipse.jgit', module = 'org.eclipse.jgit', version = '5.8.1.202007141445-r'),
        @Grab(group='org.javassist', module='javassist', version='3.27.0-GA')
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



def diffParser(List changes){
    def diffPattern = /@@(.+?)@@/

    def changeScopes = []
    changes.each{change ->
        if("MODIFY" == change.type){
            def diff = change.diff
            println(diff)
            def detachDiff = (diff =~ diffPattern).findAll()*.last()        // [ -7,9 +7,14 ,  -22,13 +27,29 ]

            // Detach changed scopes
            detachDiff.each{number ->
                def splitChanges = number.split(',')
                def indxStart = (splitChanges.first() - "-").toInteger()
                def changeScope = splitChanges.last().toInteger() - 1   // Git always count to unchanged part
                changeScopes += ["startLine": indxStart, "endLine": indxStart + changeScope]
            }
            def lstDiffs = diff.split('\n')

            println(lstDiffs)
        }
    }
    println(changeScopes)
}

def loadFile(){
    def filePath = "H:/Codebase/JGit/src/main/java/com/dl/jgit/CommitTrace.java"

    File file = new File(filePath)
    ClassLoader classLoader = getClass().getClassLoader()
    Class loadedClass = classLoader.parseClass(file); // groovySource is a File object pointing to .groovy file
    def lstMethod = loadedClass.getDeclaredMethods().findAll{it.toString().contains(".dl.")}
    def value = lstMethod.first().getMetaPropertyValues()
    println(lstMethod)  // It's hard to detach method with invoke method of java

//    GroovyClassLoader loader = new GroovyClassLoader(parent);
//    Class groovyClass = loader.parseClass(file); // groovySource is a File object pointing to .groovy file
//    GroovyObject groovyObject = (GroovyObject) groovyClass.newInstance();
//    Object[] args = {};
//    groovyObject.invokeMethod("doit", args);


//    File file = new File(filePath)
//    ClassLoader classLoader = getClass().getClassLoader()
//    Class loadedClass = classLoader.parseClass(file);
//    Method m;
//    ClassPool pool = ClassPool.getDefault();
//    pool.insertClassPath(new ClassClassPath(loadedClass));
//    CtClass cc = pool.get("CommitTrace")
//    println("")
//    CtClass cc  = classPool.get(filePath)

}


//Method m; // the method object
//ClassPool pool = ClassPool.getDefault();
//CtClass cc = pool.get(m.getDeclaringClass().getCanonicalName());
//CtMethod javassistMethod = cc.getDeclaredMethod(m.getName());
//int linenumber = javassistMethod.getMethodInfo().getLineNumber(0);

// https://stackoverflow.com/questions/16745206/javassist-using-a-jar-file
//pool.insertClassPath( "/Path/from/root/myjarfile.jar" );


//// Execute detection
//def diffs = traceDiff("H:/Codebase/JGit")
//diffParser(diffs)

loadFile()