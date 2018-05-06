package globesort;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.inf.*;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.lang.RuntimeException;
import java.lang.Exception;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GlobeSortClient {

    private final ManagedChannel serverChannel;
    private final GlobeSortGrpc.GlobeSortBlockingStub serverStub;

	private static int MAX_MESSAGE_SIZE = 100 * 1024 * 1024;

    private String serverStr;

    public GlobeSortClient(String ip, int port) {
        this.serverChannel = ManagedChannelBuilder.forAddress(ip, port)
				.maxInboundMessageSize(MAX_MESSAGE_SIZE)
                .usePlaintext(true).build();
        this.serverStub = GlobeSortGrpc.newBlockingStub(serverChannel);

        this.serverStr = ip + ":" + port;
    }

    public long run(Integer[] values) throws Exception {
        System.out.println("Pinging " + serverStr + "...");
        long startTime = System.nanoTime();

        serverStub.ping(Empty.newBuilder().build());

        long elapsedTime = System.nanoTime() - startTime;

        System.out.println("Total Ping Time (Latency): " + elapsedTime + " ns");
        System.out.println("Half Ping Time (Half Latency): " + elapsedTime / 2.0 + " ns");

        System.out.println("Ping successful.");

        System.out.println("Requesting server to sort array");

        IntArray request = IntArray.newBuilder().addAllValues(Arrays.asList(values)).build();
        ServResponse response = serverStub.sortIntegers(request);

        SortTime sortTime = response.getSortTime();
        long timeToSort = sortTime.getSortTime();

        System.out.println("Sort on Server Time taken: " + timeToSort + " ns");

        System.out.println("Sorted array");
        return timeToSort;

    }

    public void shutdown() throws InterruptedException {
        serverChannel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
    }

    private static Integer[] genValues(int numValues) {
        ArrayList<Integer> vals = new ArrayList<Integer>();
        Random randGen = new Random();
        for(int i : randGen.ints(numValues).toArray()){
            vals.add(i);
        }
        return vals.toArray(new Integer[vals.size()]);
    }

    private static Namespace parseArgs(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("GlobeSortClient").build()
                .description("GlobeSort client");
        parser.addArgument("server_ip").type(String.class)
                .help("Server IP address");
        parser.addArgument("server_port").type(Integer.class)
                .help("Server port");
        parser.addArgument("num_values").type(Integer.class)
                .help("Number of values to sort");

        Namespace res = null;
        try {
            res = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        return res;
    }

    public static void main(String[] args) throws Exception {
        long startTime = System.nanoTime();

        Namespace cmd_args = parseArgs(args);
        if (cmd_args == null) {
            throw new RuntimeException("Argument parsing failed");
        }
        int numValues=cmd_args.getInt("num_values");
        Integer[] values = genValues(numValues);

        GlobeSortClient client = new GlobeSortClient(cmd_args.getString("server_ip"), cmd_args.getInt("server_port"));
        long timeToSort;
        try {
            timeToSort = client.run(values);
        } finally {
            client.shutdown();
        }

        long elapsedTime = System.nanoTime() - startTime;
        long networkThroughputTime = elapsedTime - timeToSort;
        System.out.println("Total Application Throughput Time: " + elapsedTime + " ns");
        System.out.println("Application throughput: " + elapsedTime*1.0/numValues + " number of intergers sorted per second");
        System.out.println("Total Network Throughput Time: " + networkThroughputTime + " ns" );
        System.out.println("One way Network Throughput Time: " + networkThroughputTime*1.0/2.0 + " ns" );

    }
}
