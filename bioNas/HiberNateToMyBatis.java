import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
public class HiberNateToMyBatis{
	private  Connection con = null;
	private  final String DRIVER_CLASS = "oracle.jdbc.driver.OracleDriver";
    private  final String DATABASE_URL = "jdbc:oracle:thin:@192.168.1.213:1521:oragafis";
    private  final String DATABASE_USER = "gafis_qd_new";
    private  final String DATABASE_PASSWORD = "gafis";
    private  Map<String,String> oracleTypeMap = null;
    /**
     * 若有关联对象，放入对象中关联的属性名称和所关联对象的主键属性名称
     */
    private static Map<String,String> entityValueMap = new HashMap<String,String>();
    
	public static void main(String[] args) {
		String hibernatePath = "C:\\Users\\Administrator\\Desktop\\1\\GatherPalm.hbm.xml";//hibernate文件存放路径，包括文件名
		String myBatisPath = "C:\\Users\\Administrator\\Desktop\\1";//mybatis文件存放路径，不包括文件名
		String daoPackage = "com.gfs.finger.dao";//工程中dao文件所在包名，如com.gfs.finger.dao
		HiberNateToMyBatis h = new HiberNateToMyBatis();
		entityValueMap.put("person", "person.personId");
		entityValueMap.put("inputPsn", "inputPsn.pkId");
		entityValueMap.put("modifiedPsn", "modifiedPsn.pkId");
		h.singleHibernateToMybatis(hibernatePath, myBatisPath, daoPackage,1);
	}
	
	public String getEntityValue(String key){
		String result = entityValueMap.get(key);
		if(result == null || "".equals(result)){
			return key;
		}
		return result;
	}
	
	public boolean cotainInEntityValueMap(String key){
		String result = entityValueMap.get(key);
		if(result == null || "".equals(result)){
			return false;
		}
		return true;
	}
	
	/**
	 * hibernate配置文件转mybatis(单个)
	 * @param hibernatePath
	 * @param myBatisPath
	 * @param daoPackage
	 * @param primartKeyType 主键自动生成类型，0：无需自动生成，1：插入自动生成uuid主键
	 */
	public  void singleHibernateToMybatis(String hibernatePath,String myBatisPath,String daoPackage,
			Integer primartKeyType) {
		//hiberNateMapping
		File hibernateFile = new File(hibernatePath);
		String tableName = "";//表名
		String entityName = "";//实体名称
		String id = "";//主键名称
		String entityPackageStr = "";//entity包名称
		Map<String, String> map = new LinkedHashMap<String,String>();//存储列和实体属性的映射关系，LinkedHashMap用于顺序存储
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();   
			DocumentBuilder builder = factory.newDocumentBuilder(); 
			System.out.println("==========正在解析=========");
			Document doc = builder.parse(hibernateFile);  
			System.out.println("==========解析完成=========");
			//获取存放对象的包名
			NodeList entityPackage = doc.getElementsByTagName("hibernate-mapping"); 
			Element packageElement = (Element) entityPackage.item(0);
			entityPackageStr = packageElement.getAttribute("package");
			//获取对象名称和表名
			NodeList clazz = doc.getElementsByTagName("class"); 
			Element clazzElement = (Element) clazz.item(0);
			tableName = clazzElement.getAttribute("table").toUpperCase();
			entityName = clazzElement.getAttribute("name");
			//读取列和实体的映射关系
			NodeList idList = doc.getElementsByTagName("id");
			if(idList.getLength() > 0){
				Node idNode = idList.item(0);
				Element idElement = (Element) idNode;
				id = idElement.getAttribute("column");
				if(id == null || id.equals("")){
					NodeList idList2 = idNode.getChildNodes();
					for(int i=0;i<idList2.getLength();i++){
						if("column".equals(idList2.item(i).getNodeName())){
							Element idElement2 = (Element) idList2.item(i);
							id = idElement2.getAttribute("name");
							break;
						}
					}
				}
				if(id == null || id.equals("")){
					throw new Exception("获取不到列名，请检查配置文件");
				}
				map.put(id.toUpperCase(), idElement.getAttribute("name"));
			}
			NodeList propertyList = doc.getElementsByTagName("property");
			if(propertyList.getLength() > 0){
				for(int i=0;i<propertyList.getLength();i++){
					Node proNode = propertyList.item(i);
					Element proElement = (Element) proNode;
					String column = proElement.getAttribute("column");
					if(column == null || column.equals("")){
						NodeList propertyList2 = proNode.getChildNodes();
						for(int j=0;j<propertyList2.getLength();j++){
							if("column".equals(propertyList2.item(j).getNodeName())){
								Element proElement2 = (Element) propertyList2.item(j);
								map.put(proElement2.getAttribute("name").toUpperCase(), proElement.getAttribute("name"));
								break;
							}
						}
					}else{
						map.put(proElement.getAttribute("column").toUpperCase(), proElement.getAttribute("name"));
					}
				}
			}
			NodeList manyToOneList = doc.getElementsByTagName("many-to-one");
			for(int i=0;i<manyToOneList.getLength();i++){
				Node manyToOneNode = manyToOneList.item(i);
				Element manyToOneNodeElement = (Element) manyToOneNode;
				String column = manyToOneNodeElement.getAttribute("column");
				String property = manyToOneNodeElement.getAttribute("name");
				if(column == null || "".equals(column)){
					NodeList manyToOneList2 = manyToOneNode.getChildNodes();
					for(int j=0;j<manyToOneList2.getLength();j++){
						if("column".equals(manyToOneList2.item(j).getNodeName())){
							Element manyToOneNodeElement2 = (Element) manyToOneList2.item(j);
							column = manyToOneNodeElement2.getAttribute("column");
							break;
						}
					}
				}
				map.put(column.toUpperCase(), property);
			}
			//MyBatis文件生成路径
			File myBatisFile = new File(myBatisPath+File.separator+getCamelCase(tableName)+"Mapper.xml");
			if(myBatisFile.exists()){
				myBatisFile.createNewFile();
			}
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(myBatisFile))); 
			writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			writer.newLine();
			writer.write("<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">");
			writer.newLine();
			//mapper start
			writer.write("<mapper namespace=\"");
			if(daoPackage == null || "".equals(daoPackage)){
				throw new Exception("daoPackage为空");
			}
			writer.write(daoPackage+"."+getCamelCase(tableName)+"Mapper\">");writer.newLine();
			
			//resultMap start
			writer.write("  <resultMap id=\"BaseResultMap\" type=\"");
			writer.write(entityPackageStr+"."+entityName+"\">");
			writer.newLine();
			StringBuffer s = new StringBuffer("    ");
			for(Map.Entry<String, String> entry : map.entrySet()){
				String column = entry.getKey();
				String property = entry.getValue();
				s = new StringBuffer("    ");
				if(column.equals(id)){
					//主键
					s.append("<id column=\"").append(column).append("\" ")
					.append("jdbcType=\"").append(getColumnType(column,tableName)).append("\" ")
					.append("property=\"").append(property).append("\" />");
				}else{
					//非主键
					s.append("<result column=\"").append(column).append("\" ")
					.append("jdbcType=\"").append(getColumnType(column,tableName)).append("\" ")
					.append("property=\"").append(getEntityValue(property)).append("\" />");
				}
				writer.write(s.toString());
				writer.newLine();
			}
			writer.write("  </resultMap>");
			writer.newLine();
			//resultMap end
			
			//Base_Column_List start
			writer.write("  <sql id=\"Base_Column_List\">");
			writer.newLine();
			int index = 0;
			s = new StringBuffer("    ");
			for(Map.Entry<String, String> entry : map.entrySet()){
				index++;
				String column = entry.getKey();
				if(index%6 == 0){
					//5个换行
					s.append("\n").append("    ");
				}
				s.append(column).append(",");
			}
			writer.write(s.substring(0, s.length()-1));
			writer.newLine();
			writer.write("  </sql>");
			writer.newLine();
			//Base_Column_List end
			
			//selectByPrimaryKey start
			if(id != null && !"".equals(id)){
				s = new StringBuffer("  <select id=\"selectByPrimaryKey\" parameterType=\"java.lang.String\" resultMap=\"BaseResultMap\">\n")
						.append("    select\n")
						.append("    <include refid=\"Base_Column_List\" />\n")
						.append("    from ").append(tableName).append("\n")
						.append("    where ").append(id).append(" = #{").append(map.get(id)).append(",jdbcType=")
						.append(getColumnType(id, tableName)).append("}\n");
				writer.write(s.append("  </select>").toString());
				writer.newLine();
			}
			//selectByPrimaryKey end
			
			//deleteByPrimaryKey start
			if(id != null && !"".equals(id)){
				s = new StringBuffer("  <delete id=\"deleteByPrimaryKey\" parameterType=\"java.lang.String\">\n")
						.append("    delete from ").append(tableName)
						.append("\n    where ").append(id).append(" = #{").append(map.get(id)).append(",jdbcType=")
						.append(getColumnType(id, tableName)).append("}\n");
				writer.write(s.append("  </delete>").toString());
				writer.newLine();
			}
			//deleteByPrimaryKey end
			
			//insert start
			s = new StringBuffer("  <insert id=\"insert\" parameterType=\"").append(entityPackageStr).append(".")
					.append(entityName).append("\">\n");
			if(!id.equals("") && 1 == primartKeyType){
				s.append("    <selectKey keyProperty=\"").append(map.get(id)).append("\" resultType=\"String\" order=\"BEFORE\">\n")
				.append("      select sys_guid() from dual\n")
				.append("    </selectKey>\n");
			}
			s.append("    insert into ").append(tableName).append(" (\n    ");
			index = 0;
			StringBuffer columnStr = new StringBuffer();
			StringBuffer value = new StringBuffer("\n    values(");
			for(Map.Entry<String, String> entry : map.entrySet()){
				index++;
				String column = entry.getKey();
				String property = entry.getValue();
				columnStr.append(column).append(", ");
				if(!cotainInEntityValueMap(property)){
					value.append("#{").append(property).append(",jdbcType=").append(getColumnType(column, tableName)).append("}, ");
					if(index%6==0){
						columnStr.append("\n    ");
					}
					if(index%3==0){
						value.append("\n    ");
					}
				}
			}
			s.append(columnStr.deleteCharAt(columnStr.lastIndexOf(",")).append(")"));
			if(entityValueMap.size() <= 0){
				s.append(value.deleteCharAt(value.lastIndexOf(",")));
			}else{
				s.append(value);
			}
			s.append("\n");
			value = new StringBuffer();
			index = 0;
			for(Map.Entry<String, String> entry : map.entrySet()){
				index++;
				String column = entry.getKey();
				String property = entry.getValue();
				if(cotainInEntityValueMap(property)){
					value.append("    <choose>\n")
						.append("      <when test=\"").append(property)
						.append(" != null and ").append(getEntityValue(property))
						.append(" != null\">\n");
					value.append("        #{").append(getEntityValue(property)).append(",jdbcType=").append(getColumnType(column, tableName)).append("}");
					if(index < map.size()){
						value.append(",\n");
					}else{
						value.append("\n");
					}
					value.append("      </when>\n      <otherwise>\n");
					if(index < map.size()){
						value.append("        '',\n");
					}else{
						value.append("        ''\n");
					}
					value.append("      </otherwise>\n    </choose>\n");
				}
			}
			s.append(value);
			s.append("      )\n");
			writer.write(s.append("  </insert>").toString());
			writer.newLine();
			//insert end
			
			
			//insertSelective start
			s = new StringBuffer("  <insert id=\"insertSelective\" parameterType=\"").append(entityPackageStr).append(".")
					.append(entityName).append("\">\n")
					.append("    insert into ").append(tableName).append("\n");
			columnStr = new StringBuffer("    <trim prefix=\"(\" suffix=\")\" suffixOverrides=\",\">\n");
			value = new StringBuffer("    <trim prefix=\"values (\" suffix=\")\" suffixOverrides=\",\">\n");
			for(Map.Entry<String, String> entry : map.entrySet()){
				String column = entry.getKey();
				String property = entry.getValue();
				if(cotainInEntityValueMap(property)){
					columnStr.append("      <if test=\"").append(property).append(" != null and ")
					.append(getEntityValue(property)).append(" != null\">\n        ")
					.append(column).append(",\n      </if>\n");
					value.append("      <if test=\"").append(property).append(" != null and ")
					.append(getEntityValue(property)).append(" != null\">\n        ")
					.append("#{").append(getEntityValue(property)).append(",jdbcType=").append(getColumnType(column, tableName)).append("},")
					.append("\n      </if>\n");
				}else{
					columnStr.append("      <if test=\"").append(property).append(" != null\">\n        ")
					.append(column).append(",\n      </if>\n");
					value.append("      <if test=\"").append(property).append(" != null\">\n        ")
					.append("#{").append(property).append(",jdbcType=").append(getColumnType(column, tableName)).append("},")
					.append("\n      </if>\n");
				}
			}
			columnStr.append("    </trim>\n");
			value.append("    </trim>\n");
			s.append(columnStr).append(value).append("  </insert>\n");
			writer.write(s.toString());
			writer.newLine();
			//insertSelective end
			
			//updateByPrimaryKeySelective start
			if(id != null && !"".equals(id)){
				s = new StringBuffer("  <update id=\"updateByPrimaryKeySelective\" parameterType=\"").append(entityPackageStr).append(".")
						.append(entityName).append("\">\n")
						.append("    update ").append(tableName).append("\n    <set>\n");
				index = 0;
				for(Map.Entry<String, String> entry : map.entrySet()){
					String column = entry.getKey();
					String property = entry.getValue();
					if(!id.equals(column)){
						if(cotainInEntityValueMap(property)){
							s.append("      <if test=\"").append(property).append(" != null and ")
							.append(getEntityValue(property)).append(" != null\">\n        ")
							.append(column).append(" = #{").append(getEntityValue(property)).append(",jdbcType=").append(getColumnType(column, tableName)).append("},")
							.append("\n      </if>\n");
						}else{
							s.append("      <if test=\"").append(property).append(" != null\">\n        ")
							.append(column).append(" = #{").append(property).append(",jdbcType=").append(getColumnType(column, tableName)).append("},")
							.append("\n      </if>\n");
						}
					}
				}
				s.append("    </set>\n    where ").append(id)
				.append(" = #{").append(map.get(id)).append(",jdbcType=").append(getColumnType(id, tableName)).append("}\n")
				.append("  </update>\n");
				writer.write(s.toString());
				writer.newLine();
			}
			//updateByPrimaryKeySelective end
			
			//updateByPrimaryKey start
			if(id != null && !"".equals(id)){
				s = new StringBuffer("  <update id=\"updateByPrimaryKey\" parameterType=\"").append(entityPackageStr).append(".")
						.append(entityName).append("\">\n")
						.append("    update ").append(tableName).append(" set");
				for(Map.Entry<String, String> entry : map.entrySet()){
					String column = entry.getKey();
					String property = entry.getValue();
					if(!id.equals(column)){
						if(!cotainInEntityValueMap(property)){
							s.append("\n    ").append(column).append(" = #{").append(property).append(",jdbcType=").append(getColumnType(column, tableName)).append("},");
						}
					}
				}
				if(entityValueMap.size() > 0){
					//s = new StringBuffer(s.substring(0, s.length()-1));
				}else{
					s = new StringBuffer(s.substring(0, s.length()-1));
				}
				s.append("\n");
				index = 0;
				value = new StringBuffer();
				for(Map.Entry<String, String> entry : map.entrySet()){
					index++;
					String column = entry.getKey();
					String property = entry.getValue();
					if(cotainInEntityValueMap(property)){
						value.append("    "+column).append(" = \n");
						value.append("    <choose>\n")
							.append("      <when test=\"").append(property)
							.append(" != null and ").append(getEntityValue(property))
							.append(" != null\">\n");
						value.append("        #{").append(getEntityValue(property)).append(",jdbcType=").append(getColumnType(column, tableName)).append("}");
						if(index < map.size()){
							value.append(",\n");
						}else{
							value.append("\n");
						}
						value.append("      </when>\n      <otherwise>\n");
						if(index < map.size()){
							value.append("        '',\n");
						}else{
							value.append("        ''\n");
						}
						value.append("      </otherwise>\n    </choose>\n");
					}
				}
				s.append(value);
				s.append("    where ").append(id)
				.append(" = #{").append(map.get(id)).append(",jdbcType=").append(getColumnType(id, tableName)).append("}\n");
				s.append("  </update>");
				writer.write(s.toString());
				writer.newLine();
			}
			//updateByPrimaryKey end
			
			writer.write("</mapper>");
			//mapper end
			writer.newLine();
			writer.flush();
			writer.close();
		} catch (ConnectException e) {
			System.out.println("去除hibernate配置文件中的dtd");
		}catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("end");
	}
	
	/**
	 * 查找列的数据类型
	 * @param column
	 * @param tableName
	 * @return
	 * @throws Exception 
	 */
	private  String getColumnType(String column,String tableName) throws Exception{
		if(oracleTypeMap == null){
			oracleTypeMap = new HashMap<String,String>();
			getConnection();
			Statement s = con.createStatement();
			String sql = "select column_name,data_type from   cols  "+   
					"WHERE   TABLE_name=upper('"+ tableName +"') ";
			ResultSet rs = s.executeQuery(sql);
			String oracleType = "";
			String columuName = "";
			while(rs.next()){
				columuName = rs.getString(1).toUpperCase();
				oracleType = rs.getString(2).toUpperCase();
				
				
				if("recordMark".toUpperCase().equals(columuName)){
					System.out.println(oracleType);
				}
				if("VARCHAR2".equals(oracleType)){
					oracleType = "VARCHAR";
				}else if("NUMBER".equals(oracleType)){
					oracleType = "DECIMAL";
				}else if("DATE".equals(oracleType)){
					//TIMESTAMP带时分秒，DATE只有年月日
					oracleType = "TIMESTAMP";
				}
				oracleTypeMap.put(columuName, oracleType);
			}
			rs.close();
			s.close();
			con.close();
		}
		return oracleTypeMap.get(column);
	}
	
	/**
	 * 驼峰命名
	 * @return
	 */
	private  String getCamelCase(String name){
		String s = "";
		String[] arr = name.toLowerCase().split("_");
		for(String a : arr){
			s += a.substring(0, 1).toUpperCase() + a.substring(1);
		}
		return s;
	}
	
	//获取数据库连接
	private  Connection getConnection(){
		try {
            Class.forName(DRIVER_CLASS);
            con=DriverManager.getConnection(DATABASE_URL,DATABASE_USER,DATABASE_PASSWORD);
            return con;
        } catch (Exception ex) {
            System.out.println("2:"+ex.getMessage());
        }
        return con;
	}
	
}