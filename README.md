# auditing-with-error-correction
The auditing-with-error-correction uses the idea of integrating linear error correcting codes and linear homomorphic authentication schemes together. This integration uses only one additional block to achieve error tolerance and authentication simultaneously. This scheme based on the proposed general construction using the Reed Solomon code and the universal hash based MAC authentication scheme, both of which are implemented over the computation-efﬁcient Galois ﬁeld GF(28). 

There are three packages includes eleven classes:AuditingwithErrorCorrection package, ReedSolomon package and Run package. The AuditingwithErrorCorrection is the main package to implement the proposed scheme, while the ReedSolomon package implements the reed-solomon code and the Run package is the package where the main function and performance tests are performed.

# Build
We use Java 1.8.0_121 to develop this project and use Eclipse to compile it.

# Usage
There are two ways to run the program. If you are using Eclipse for Java development, you can do it as follows:
1.	Import our source code project into Eclipse. Try the menu “File -> Import -> Existing Projects into Workspace”.
2.	Locate the file “Running.java” which is the entrance of the whole program.
3.	Replace the string "C:\\Users\\Administrator\\Desktop\\100MB.rar" with your destination directory which stores the data that to be outsourced.
4.	The performance result will be output in the console window.

If you are used to compile a java program in the command line, you can do it as follows:
1.	Unzip the source code. Find all source codes in the directory “../src/”, compile all the source code.
2.	Locate the file “Running.java” which is the entrance of the whole program.
3.	Replace the string " C:\\Users\\Administrator\\Desktop\\100MB.rar" with your destination directory which stores the data that to be outsourced. Compile the program and then run it.
4.	The performance result will be output in the console window.

Note that we employ a third-party utility class MemoryUtil to measure the size of a running-time object in the memory.  To use this class, you need to send a parameter to the Java Virtual Machine: -javaagent:classmexer.jar. For more information about MemoryUtil, please refer to http://www.javamex.com/classmexer/.

