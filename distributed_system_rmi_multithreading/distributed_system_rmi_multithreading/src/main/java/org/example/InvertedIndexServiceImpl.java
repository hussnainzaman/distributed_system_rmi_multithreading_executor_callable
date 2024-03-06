package org.example;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Implementation of the InvertedIndexService interface
public class InvertedIndexServiceImpl extends UnicastRemoteObject implements InvertedIndexService {

    // ExecutorService to handle concurrent tasks
    private ExecutorService executorService;

    // Constructor
    public InvertedIndexServiceImpl() throws RemoteException {
        super();
        // Initialize the executor service with the number of available processors
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    // Method to generate inverted index for a given file
    @Override
    public Map<String, List<Integer>> getInvertedIndex(String fileName) throws RemoteException {
        // Call the getFileContent method to retrieve the content of the file as a string
        String text = new getFileContents().getFileContent(fileName);

        // Split the text into lines
        String[] lines = text.split("\n");

        // Create a map to store the inverted index
        Map<String, List<Integer>> index = new HashMap<>();

        // List to hold futures
        List<Callable<Void>> tasks = new ArrayList<>();

        // For each line, submit a task to the executor service to compute the inverted index for that line
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i;
            // Create a callable task
            Callable<Void> task = () -> {
                // Split the line into words
                String[] words = line.split("\\s+");
                // Create a map to store the inverted index for the current line
                Map<String, List<Integer>> lineIndex = new HashMap<>();
                for (int j = 0; j < words.length; j++) {
                    String word = words[j];
                    // Get the list of line numbers for the current word
                    List<Integer> lineNumbers = lineIndex.getOrDefault(word, new ArrayList<>());
                    // Add the current line number to the list
                    lineNumbers.add(lineNumber + 1); // Adding 1 because line numbers start from 1
                    // Update the map with the updated list of line numbers
                    lineIndex.put(word, lineNumbers);
                }
                // Merge the line index with the global index
                synchronized (index) {
                    lineIndex.forEach((word, lineNumbers) -> {
                        List<Integer> indexLineNumbers = index.getOrDefault(word, new ArrayList<>());
                        indexLineNumbers.addAll(lineNumbers);
                        Collections.sort(indexLineNumbers); // Sort the line numbers for consistency
                        index.put(word, indexLineNumbers);
                    });
                }
                return null;
            };
            // Add task to the list
            tasks.add(task);
        }

        // Invoke all tasks
        try {
            executorService.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } finally {
            // Shut down the executor service
            executorService.shutdown();
        }

        // Return the inverted index
        return index;
    }

    // Main method to start the RMI server
    public static void main(String args[]) {
        try {
            // Create an instance of the service implementation
            InvertedIndexServiceImpl server = new InvertedIndexServiceImpl();
            // Create and start the RMI registry on port 8099
            LocateRegistry.createRegistry(8099);
            // Bind the remote object to the registry
            Naming.rebind("//127.0.0.1:8099/InvertedIndexService", server);
            System.out.println("InvertedIndexService ready...");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
