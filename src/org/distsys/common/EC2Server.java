package org.distsys.common;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.util.EC2MetadataUtils;

import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class EC2Server extends UnicastRemoteObject {

	protected static String hostname;

	protected ExecutorService pool;

	protected EC2Server() throws Exception {
		super(1099);
		hostname = getInstancePublicDnsName();
		if (hostname == null) {
			throw new Exception("EC2 client not properly configured.");
		}
		System.setProperty("java.rmi.server.hostname", hostname);
		hostname += "/server";

		pool = Executors.newCachedThreadPool();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			pool.shutdown();
		}));
	}

	protected static String getInstancePublicDnsName() throws Exception {
		final String accessKey = System.getenv("AWS_ACCESS_KEY");
		final String secretKey = System.getenv("AWS_SECRET_KEY");
		if (accessKey == null || secretKey == null)
			throw new Exception("EC2 client not properly configured.");

		BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
		AmazonEC2 ec2 = new AmazonEC2Client(credentials);
		ec2.setEndpoint("ec2.eu-central-1.amazonaws.com");
		ec2.setRegion(Region.getRegion(Regions.EU_CENTRAL_1));
		String instanceId = EC2MetadataUtils.getInstanceId();
		DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
		List<Reservation> reservations = describeInstancesRequest.getReservations();
		for (Reservation reservation : reservations) {
			for (Instance instance : reservation.getInstances()) {
				if (instance.getInstanceId().equals(instanceId))
					return instance.getPublicDnsName();
			}
		}
		return null;
	}
}
