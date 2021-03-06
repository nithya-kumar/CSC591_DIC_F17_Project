package com.spamreviews.detection;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

public class SpamDetection {
	public static class SpamMap extends Mapper<Object, Text, Text, FloatWritable> {
		private Text reviewer = new Text();
		private static FloatWritable val = new FloatWritable(0);
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString();
			String[] tuple = line.split("\\n");
			for(int i = 0; i < tuple.length; i++) {
				JsonFactory factory = new JsonFactory();
				ObjectMapper mapper = new ObjectMapper(factory);
				JsonNode rootNode = mapper.readTree(tuple[i]);
				String content = "";
				Iterator<Entry<String,JsonNode>> fieldsIterator = rootNode.fields();
				while (fieldsIterator.hasNext()) {
					Entry<String,JsonNode> field = fieldsIterator.next();
					if (field.getKey() == "reviewerID") {
						reviewer.set(field.getValue().toString());
					}
					if (field.getKey() == "helpful") {
						content = field.getValue().toString();
						Scanner in = new Scanner(content).useDelimiter("[^0-9]+");
						int v1;
						v1 = in.nextInt();
						int v2 = in.nextInt();
						if (v2 != 0) {
							float f = (float)v1/v2;
							val.set(f);
						}
					}
				}
				context.write(reviewer, val);
			}
		}
	}
	
	public static class SpamReduce extends Reducer<Text, FloatWritable, Text, FloatWritable> {
		private FloatWritable out = new FloatWritable(0);
		public void reduce(Text key, Iterable<FloatWritable> values, Context context) throws IOException, InterruptedException {
			float sum = 0;
			float count = 0;
			double threshold = 0.30;
			for (FloatWritable val_red : values) {
				count = count + 1;	
				sum += val_red.get();
			}
			float res = sum/count;
			if(res < threshold) {
				out.set((float) 1.0);
				context.write(key, out);
			}
		}
	}
	 
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
	    Job job = Job.getInstance(conf, "spam detect");
	    job.setJarByClass(SpamDetection.class);
	    job.setMapperClass(SpamMap.class);
	    //job.setCombinerClass(SpamReduce.class);
	    job.setReducerClass(SpamReduce.class);
	    job.setOutputKeyClass(Text.class);
	    job.setOutputValueClass(FloatWritable.class);
	    FileInputFormat.addInputPath(job, new Path(args[0]));
	    FileOutputFormat.setOutputPath(job, new Path(args[1]));
	    System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
