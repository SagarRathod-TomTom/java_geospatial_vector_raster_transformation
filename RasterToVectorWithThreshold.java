import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.process.raster.PolygonExtractionProcess;
import org.opengis.feature.simple.SimpleFeatureType;
import org.geotools.data.shapefile.ShapefileDataStore;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class RasterToVectorWithThreshold {

    /**
     * Maven Dependencies
     * <dependency>
     * 			<groupId>org.geotools</groupId>
     * 			<artifactId>gt-process-raster</artifactId>
     * 			<version>${geotools.version}</version>
     * 		</dependency>
     * 		<dependency>
     * 			<groupId>org.geotools</groupId>
     * 			<artifactId>gt-epsg-extension</artifactId>
     * 			<version>${geotools.version}</version>
     * 		</dependency>
     * 		<dependency>
     * 			<groupId>org.geotools</groupId>
     * 			<artifactId>gt-epsg-hsql</artifactId>
     * 			<version>11.1</version>
     * 		</dependency>
     * 	    <dependency>
     * 			<groupId>org.geotools</groupId>
     * 			<artifactId>gt-shapefile</artifactId>
     * 			<version>${geotools.version}</version>
     * 		</dependency>
     * 		<dependency>
     * 			<groupId>org.geotools</groupId>
     * 			<artifactId>gt-geopkg</artifactId>
     * 			<version>${geotools.version}</version>
     * 		</dependency>
     *
     * @param args
     * @throws IOException
     */


    public static void main(String args[]) throws IOException {

        if(args.length < 2){
            System.out.println("Usage: java RasterToVectorWithThreshold [input_raster_dir] [out_dir] [integer_threshold_value_default_is_128]");
            return;
        }

        String rasterDir = args[0];
        File rasterDirFile = new File(rasterDir);
        File rasterFiles[] = rasterDirFile.listFiles();

        String out_dir = args[1];
        String outfilepath = out_dir + "\\" + "vector.shp";
        File outfile = new File(outfilepath);

        int threshold_value = 128;
        if(args.length > 2) {
            try {
                threshold_value = Integer.valueOf(args[2]);
                if (threshold_value < 0 || threshold_value > 255) {
                    System.out.println("Threshold value must be between [0-255].");
                    return;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid threshold value = " + args[2]);
            }
        }
        System.out.println("Using threshold Value = " + threshold_value);

        SimpleFeatureType schema = null;
        ShapefileDataStore newDataStore = null;
        GridCoverageFactory gcfactory = new GridCoverageFactory();

        for(int i = 0; i < rasterFiles.length; i++){

            String filepath = rasterFiles[i].toString();
            if(!filepath.endsWith(".tiff"))
                continue;

            GeoTiffReader tiffReader = new GeoTiffReader(rasterFiles[i]);
            GridCoverage2D coverage = tiffReader.read(null);

            Raster raster = coverage.getRenderedImage().getData();
            BufferedImage image = new BufferedImage(raster.getWidth(), raster.getHeight(),
                    BufferedImage.TYPE_BYTE_GRAY);
            image.setData(raster);

            BufferedImage thresholded = RasterToVectorWithThreshold.thresholdImage(image, 128);

            GridCoverage2D new_coverage = gcfactory.create("tiff", thresholded, coverage.getEnvelope());

            PolygonExtractionProcess process = new PolygonExtractionProcess();
            SimpleFeatureCollection simpleFeatureCollection =  process.execute(new_coverage, 0,
                    false, null, null,  null, null);

            if(schema == null) {
                schema = simpleFeatureCollection.getSchema();
                ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
                Map<String, Serializable> create = new HashMap<String, Serializable>();
                create.put("url", outfile.toURI().toURL());

                newDataStore = (ShapefileDataStore) factory.createNewDataStore(create);
                newDataStore.createSchema(schema);
            }

            Transaction transaction = new DefaultTransaction();
            SimpleFeatureStore featureStore = (SimpleFeatureStore) newDataStore.getFeatureSource();
            featureStore.setTransaction(transaction);
            try {
                featureStore.addFeatures(simpleFeatureCollection);
                transaction.commit();
            } catch (Exception problem) {
                problem.printStackTrace();
                transaction.rollback();
            } finally {
                transaction.close();
            }
            System.out.println("Completed " + i  + "/" + rasterFiles.length + " " + rasterFiles[i]);
        }


        // Geo package support in geotools
        //https://docs.geotools.org/latest/userguide/library/data/geopackage.html

        String geopkgoutfilepath = out_dir + "\\" + "vector.gpk";
        File gpkoutfile = new File(geopkgoutfilepath);
        GeoPackage geopkg = new GeoPackage(gpkoutfile);
        geopkg.init();
        FeatureEntry entry = new FeatureEntry();
        geopkg.add(entry, newDataStore.getFeatureSource(), null);
        geopkg.close();

    }

    public static BufferedImage thresholdImage(BufferedImage bufferedImage, int threshold){

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        boolean hasAlpha = bufferedImage.getAlphaRaster() != null;

        for(int row = 0 ; row < height; row++){
            for(int col = 0; col < width; col++){
                Color color = new Color(bufferedImage.getRGB(row, col), hasAlpha);
                int value = (color.getRed() + color.getGreen() + color.getBlue()) / 3;

                if(value >= threshold){
                    bufferedImage.setRGB(row, col, Color.WHITE.getRGB());
                }else{
                    bufferedImage.setRGB(row, col, Color.BLACK.getRGB());
                }
            }
        }
        return bufferedImage;
    }

}
