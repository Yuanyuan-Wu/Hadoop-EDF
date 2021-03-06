package org.yyw.HadoopEDF.ParallelProcessing.SequentialProgram;




import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import com.amazonaws.AmazonServiceException;

import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;


import net.sf.json.JSONObject;


public class EDFtoJsonyuanyuanforlargefiles {
	public static void main(String[] args) throws IOException, InterruptedException
	{

		//String EDFfilePath=null;

		long total1 = System.currentTimeMillis();
		
		for(int i=1;i<21;i++) {

			String bucketName = "yuanyuan-edf";
			//String EDFfilePath=args[0]+"/";
			String EDFfilePath=args[0]+"/"+"10-"+i+"/";
			String outputPath="Seq2"+EDFfilePath;
			//String outputPath="Seq"+args[0]+"/";
			System.out.println(EDFfilePath);

			List<String> fileNameList = new ArrayList<String>();
			GetObjectRequest request=null;
			S3Object object=null;
			try {

				AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_2).build();

				ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName).withPrefix(EDFfilePath).withDelimiter("/");
				ListObjectsV2Result objects = s3Client.listObjectsV2(req);

				for (S3ObjectSummary object1 : objects.getObjectSummaries()) {
					if(object1.getKey().contains("edf")) {
						fileNameList.add(object1.getKey());
						System.out.println("getting filename by getKey(): "+object1.getKey());
					}
				}
				long t1 = System.currentTimeMillis();

				for(String file : fileNameList) {

					//GetObjectRequest request = new GetObjectRequest(bucketName,file);
					request = new GetObjectRequest(bucketName,file);
					//S3Object object = s3Client.getObject(request);
					object = s3Client.getObject(request);
					InputStream is = object.getObjectContent();
					System.out.println(file);
					//EDFParserResult result = EDFParser.parseEDF(is);
					ToJsonFormat(is,file,EDFfilePath,outputPath,s3Client,bucketName); 
					is.close();
					System.gc();

				}


				long t2 = System.currentTimeMillis();
				System.out.println("**************" + "Total Time for each subfolder: " + (t2 - t1)/1000.0 + "s" + "**************");	
			}catch(AmazonServiceException e) {
				e.printStackTrace();
			}catch(SdkClientException e) {
				e.printStackTrace();
			}
			finally {
				if(object !=null) {
					object.close();
					System.out.println("object has been closed for each subfolder");
				}
				if(request !=null) {
					request=null;
					System.out.println("request has been null for each subfolder");
				}
			}


			//}
			long total2 = System.currentTimeMillis();
			System.out.println("**************" + "Total Time with requesting time from the begining to the end of the program: " + (total2 - total1)/1000.0 + "s" + "**************");	
			//System.out.println("**************" + "Total Time for all of subfolders without requesting time: " + (t2 - t1)/1000.0 + "s" + "**************");	
		}
		
	}

	private static void ToJsonFormat(InputStream is, String file, String EDFfilePath, String outputPath, AmazonS3 s3Client,String bucketName) 
			throws IOException {
		// TODO Auto-generated method stub
		EDFParserResult result = EDFParser.parseHeader(is);
		System.out.println("---------there is no error for java heap space with the header of result-----");

		long t_edf_read = System.currentTimeMillis();

		String edf_name = file.replace(EDFfilePath, "").replace(".edf", "").replaceAll("[^A-Za-z0-9]", "") + ".edf";
		String outputFolder = edf_name.replace(".edf", "");

		String JsonFilesPath = outputPath + outputFolder;
		// 			File temp = new File(JsonFilesPath);
		// 			temp.mkdirs();
		System.out.println(JsonFilesPath + " created");


		EDFHeader header = result.getHeader();
		int channel_no = header.getNumberOfChannels();
		int numberOfRecords=result.getHeader().getNumberOfRecords();
		JSONObject EDFheader = new JSONObject();
		EDFheader.put("idCode",result.getHeader().getIdCode().trim());
		EDFheader.put("subjectID",result.getHeader().getSubjectID().trim());
		EDFheader.put("recordingID",result.getHeader().getRecordingID().trim());
		EDFheader.put("startDate", result.getHeader().getStartDate().trim());
		EDFheader.put("startTime", result.getHeader().getStartTime().trim());
		EDFheader.put("bytesInHeader", result.getHeader().getBytesInHeader());
		EDFheader.put("formatVersion",result.getHeader().getFormatVersion().trim());
		EDFheader.put("numberOfRecords",result.getHeader().getNumberOfRecords());
		EDFheader.put("durationOfRecords",result.getHeader().getDurationOfRecords());
		EDFheader.put("numberOfChannels",result.getHeader().getNumberOfChannels());

		//String EDFheaserfileName = "EDFheader";		

		String rPathheader = JsonFilesPath+ "/EDFheader.json";
		System.out.println(rPathheader);
		s3Client.putObject(bucketName,rPathheader,EDFheader.toString());

		EDFheader.clear();
		EDFheader=null;

		JSONObject Signalheader = new JSONObject();
		//get signal values
		double[][] valuesInUnits= new double[channel_no][] ;

		Double[] unitsInDigit = new Double[channel_no];
		Double[] dc = new Double[channel_no];
		Double[] maxInUnits = result.getHeader().getMaxInUnits();
		Double[] minInUnits = result.getHeader().getMinInUnits();
		Integer[] digitalMin = result.getHeader().getDigitalMin();
		Integer[] digitalMax = result.getHeader().getDigitalMax();

		for (int i = 0; i < unitsInDigit.length; i++){//unitsInDigit is intialized as the number of channels, should be 14, so i=[0:14]
			unitsInDigit[i] = (maxInUnits[i] - minInUnits[i])
					/ (digitalMax[i] - digitalMin[i]);//unitsInDigit is saclefac same as in matlab, actually it unitsInDigit is scale of offset or value of offset.
			//System.out.println( unitsInDigit[i]);
			dc[i]=maxInUnits[i] -unitsInDigit[i] *digitalMax[i];
		}
		//short[][] digitalValues = new short[channel_no][];

		Integer[] numberofsamples =header.getNumberOfSamples();
		//valuesInUnits = new double[channel_no][];
		System.out.println("bingo");

		System.gc();

		for (int i = 0; i < channel_no; i++)
		{
			//digitalValues[i] = new short[numberOfRecords * numberofsamples[i]];//such as digitalvalues[1]=10800*1, digitalvalues[2]=10800*125..
			valuesInUnits[i] = new double[numberOfRecords * numberofsamples[i]];
		}
		int samplesPerRecord = 0;//�Ա���samplesPerrecord���г�ʼ��
		for (int nos : header.numberOfSamples)//�����ŵ�1��ֵΪ1����the number of Records of channel 1 should be 10800, ���ŵ���ֵΪ125ʱ����ÿһ�μ�¼��record������125�Σ�����record[1]=[1...125], record[2]=[1...125],...record[10800]=[1..125],һ�������ŵ�2������Ϊ125*10800 
		{
			samplesPerRecord += nos;
		}
		for (int i = 0; i < header.numberOfRecords; i++)
		{       
			//                             bytebuf.rewind();//You can use rewind( ) to go back and reread the data in a buffer that has already been flipped. As you can see: it doesn't change the limit. Sometimes, after read data from a buffer, you may want to read it again, rewind() is the right choice for you.
			//                      
			//                             ch.read(bytebuf);// read() places read bytes at the buffer's position so the position should always be properly set before calling read() This method sets the position to 0
			//                             bytebuf.rewind();// The read() method also moves the position so in order to read the new bytes, the buffer's position must be set back to 0
			//                            
			int len;
			int pos = 0;
			byte[] buffer = new byte[1];
			int size = samplesPerRecord*2;
			byte[] data = new byte[size];
			while(pos < size && (len = is.read(buffer)) != -1) {
				data[pos] = buffer[0];
				pos++;
			}
			short[] sa = new short[samplesPerRecord];
			ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(sa);
			int m=0;
			for (int j = 0; j < header.numberOfChannels; j++)
				for (int k = 0; k < header.numberOfSamples[j]; k++)
				{
					int s = header.numberOfSamples[j] * i + k;
					//digitalValues[j][s] = sa[m];// getShort(byte[] bArray, short bOff) Concatenates two bytes in a byte array to form a short value,byte has 8 bits, however has 16 bits
					valuesInUnits[j][s] = sa[m] * unitsInDigit[j]+dc[j];//this equation is refered by program of matlab
					m++;
					//System.out.println(valuesInUnits[0][1]+","+valuesInUnits[0][1]+","+valuesInUnits[0][2]);
				}
		}
		//is.close();


		//output the signal header,split signal values based on fragment for each channel

		for(int i_sig = 0; i_sig < channel_no; i_sig++){
			Map<String, Object> SignalInfo = new LinkedHashMap<String, Object>();
			String label = header.getChannelLabels()[i_sig];
			String transducer = header.getTransducerTypes()[i_sig];
			String physical_dimension = header.getDimensions()[i_sig];
			double physical_minimum = header.getMinInUnits()[i_sig];
			double physical_maximum = header.getMaxInUnits()[i_sig];
			double digital_minimum = header.getDigitalMin()[i_sig];
			double digital_maximum = header.getDigitalMax()[i_sig];
			String prefiltering = header.getPrefilterings()[i_sig];
			double samples_per_data_record = header.getNumberOfSamples()[i_sig];
			String reserved_area = String.valueOf(header.getReserveds()[i_sig]);
			//the length of signal
			int total_samples = valuesInUnits[i_sig].length;
			if(label.trim().equals(""))
				label = "unkwn" +  i_sig;
			if(physical_dimension.trim().equals(""))
				physical_dimension = " ";
			//				SignalInfo.put("label", label.trim());
			SignalInfo.put("transducer", transducer.trim());
			SignalInfo.put("physical_dimension", physical_dimension.trim());
			SignalInfo.put("physical_minimum", physical_minimum);
			SignalInfo.put("physical_maximum", physical_maximum);
			SignalInfo.put("digital_minimum", digital_minimum);
			SignalInfo.put("digital_maximum", digital_maximum);
			SignalInfo.put("prefiltering", prefiltering.trim());
			SignalInfo.put("samples_per_data_record", samples_per_data_record);
			SignalInfo.put("reserved_area", reserved_area.trim());
			SignalInfo.put("total_samples", total_samples);
			Signalheader.put(label.trim(), SignalInfo);

			int actualNumofSamples = (int) (header.getNumberOfSamples()[i_sig]/header.getDurationOfRecords());
			//header = null;
			int sig_pre_loc = 0;
			int frag_index = 0;
			int sig_loc = 1*actualNumofSamples - 1;

			StringBuffer ValuesOfEachChannel = new StringBuffer();

			//			    String ValuesOfEachChannel="";
			int signal_len = valuesInUnits[i_sig].length;

			while (sig_loc < signal_len){
				frag_index = frag_index + 1;
				ArrayList<Double> fragment = new ArrayList<Double>();
				for(int iv = sig_pre_loc; iv<= sig_loc; iv++){
					fragment.add(valuesInUnits[i_sig][iv]);
				}
				//String SignalValue = "";
				if(frag_index==1) {
					ValuesOfEachChannel.append("{"+"\""+frag_index+"\""+":"+"\""+fragment.toString()+"\""+",");

				}else if(frag_index==numberOfRecords) {
					ValuesOfEachChannel.append("\""+frag_index+"\""+":"+"\""+fragment.toString()+"\""+"}");

				}else {
					ValuesOfEachChannel.append("\""+frag_index+"\""+":"+"\""+fragment.toString()+"\""+",");

				}
				fragment.clear();

				//System.out.println(label+" : "+ValuesOfEachChannel.length());

				sig_pre_loc = sig_loc + 1;
				sig_loc = sig_loc + 1*actualNumofSamples;
			}

			//String rPathSignalValueFile = JsonFilesPath+ "/" + label.trim() + ".json";
			String rPathSignalValueFile = JsonFilesPath+ "/" + label.trim();
			//System.out.println(rPathSignalValueFile);


			int length = ValuesOfEachChannel.length();
			System.out.println(rPathSignalValueFile+","+ "length of each channel"+":"+length);
			 //length>718400363
						      if(length>700000000) {
						    	  
						    	  int m =(int) Math.ceil((double) length/700000000);
						    	 // System.out.println(m);
						    	  int len=(int) Math.floor((double) length/m); 
						    	 // System.out.println(len);
						    	  int start=0;
						    	  int end=len-1;
			//			    	  System.out.println("before start"+":"+start);
			//			    	  System.out.println("before end"+":"+end);
						    	 
						    	  String subValue=null;
						    	  String subfile=null;
						    	  int i=0;
						    	  if(i==0) {
						    		  i=i+1;
						    		  subValue=ValuesOfEachChannel.substring(start, end);
									  subfile=rPathSignalValueFile+"-"+i+".json";
							    	  s3Client.putObject(bucketName,subfile,subValue);
							    	  //start=end+1;
							    	  start=end;
						    	      end=start+len-1; 
						    	      System.out.println(subfile);
			//			    	      System.out.println("first start"+":"+start);
			//			    	      System.out.println("first end"+":"+end);
						    	      
						    	  }
						    	  if(m==2) {
						    		  i=i+1;
						    		  end =length;
						    		  System.out.println("second start"+":"+start);
						    	      System.out.println("second end"+":"+end);
									  subValue=ValuesOfEachChannel.substring(start, end);
									  subfile=rPathSignalValueFile+"-"+i+".json";
									  System.out.println(subfile);
							    	  s3Client.putObject(bucketName,subfile,subValue);
						    		  
						    	  }
						    	  
							      while(m>2 && i<m-1){
							    	 // ValuesOfEachChannel.equals("],");
							    	  i=i+1;
							    	  System.out.println("middle"+i+"start"+":"+start);
							    	  System.out.println("middle"+i+"end"+":"+end);
									  subValue=ValuesOfEachChannel.substring(start, end);
									  subfile=rPathSignalValueFile+"-"+i+".json";
							    	  s3Client.putObject(bucketName,subfile,subValue);
							    	  //start=end+1;
							    	  start=end;
							    	  end=start+len-1; 
							    	  System.out.println(subfile);
							    	  System.out.println("after middle start"+":"+start);
							    	  System.out.println("after middle end"+":"+end);
							      }
							      if(m>2 && i==m-1 ) {
							      i=i+1;
								  end=length;
								  System.out.println("last start"+":"+start);
								  System.out.println("last end"+":"+end);
							      subValue=ValuesOfEachChannel.substring(start, end);
								  subfile=rPathSignalValueFile+"-"+i+".json";
								  System.out.println(subfile);
						    	  s3Client.putObject(bucketName,subfile,subValue);
							      }
							      
							     // subValue=null;
							      
						      }else{
						    	  
						    	  s3Client.putObject(bucketName,rPathSignalValueFile+".json",ValuesOfEachChannel.toString());
						    	  
						      }
			//s3Client.putObject(bucketName,rPathSignalValueFile+".json",ValuesOfEachChannel.toString());

			ValuesOfEachChannel.setLength(0);

		}

		valuesInUnits=null;
		unitsInDigit=null;
		dc=null;
		maxInUnits=null;
		minInUnits=null;
		digitalMin=null;
		digitalMax=null;
		String rPathSignalHeader = JsonFilesPath+ "/SignalHeader.json";

		s3Client.putObject(bucketName,rPathSignalHeader,Signalheader.toString());
		//s3Client.abortMultipartUpload(request);
		Signalheader.clear();
		Signalheader=null;
		System.gc();
		long sysDatetau2 = System.currentTimeMillis();
		System.out.println("**************" + "Data Successfully Uploaded: " + (sysDatetau2 - t_edf_read)/1000.0 + "s" + "**************");	
	}

}
