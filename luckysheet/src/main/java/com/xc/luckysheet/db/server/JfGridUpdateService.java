package com.xc.luckysheet.db.server;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.xc.common.utils.JsonUtil;
import com.xc.luckysheet.JfGridConfigModel;
import com.xc.luckysheet.db.IRecordDataInsertHandle;
import com.xc.luckysheet.db.IRecordDataUpdataHandle;
import com.xc.luckysheet.db.IRecordDelHandle;
import com.xc.luckysheet.db.IRecordSelectHandle;
import com.xc.luckysheet.entity.ConfigMergeModel;
import com.xc.luckysheet.entity.GridRecordDataModel;
import com.xc.luckysheet.entity.LuckySheetGridModel;
import com.xc.luckysheet.entity.enummodel.DisabledTypeEnum;
import com.xc.luckysheet.entity.enummodel.SheetOperationEnum;
import com.xc.luckysheet.redisserver.GridFileRedisCacheService;
import com.xc.luckysheet.redisserver.RedisLock;
import com.xc.luckysheet.util.JfGridFileUtil;
import com.xc.luckysheet.utils.GzipHandle;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * @author Administrator
 */
@Slf4j
@Service
public class JfGridUpdateService {

    //@Resource(name = "postgresRecordDataInsertHandle")
    //private IRecordDataInsertHandle recordDataInsertHandle;
    //
    //@Resource(name = "postgresRecordDataUpdataHandle")
    //private IRecordDataUpdataHandle recordDataUpdataHandle;
    //
    //@Resource(name = "postgresRecordDelHandle")
    //private IRecordDelHandle recordDelHandle;
    //
    //@Resource(name = "postgresRecordSelectHandle")
    //private IRecordSelectHandle recordSelectHandle;

    @Resource(name = "mysqlRecordDataInsertHandle")
    private IRecordDataInsertHandle recordDataInsertHandle;
    @Resource(name = "mysqlRecordDataUpdataHandle")
    private IRecordDataUpdataHandle recordDataUpdataHandle;
    @Resource(name = "mysqlRecordDelHandle")
    private IRecordDelHandle recordDelHandle;
    @Resource(name = "mysqlRecordSelectHandle")
    private IRecordSelectHandle recordSelectHandle;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private GridFileRedisCacheService gridFileRedisCacheService;

    public static GridRecordDataModel strToModel(String list_id, String index, int status, int order) {
        String strSheet = "{\"row\":84,\"name\":\"reSheetName\",\"chart\":[],\"color\":\"\",\"index\":\"reIndex\",\"order\":reOrder,\"column\":60,\"config\":{},\"status\":reStatus,\"celldata\":[],\"ch_width\":4748,\"rowsplit\":[],\"rh_height\":1790,\"scrollTop\":0,\"scrollLeft\":0,\"visibledatarow\":[],\"visibledatacolumn\":[],\"jfgird_select_save\":[],\"jfgrid_selection_range\":{}}";
        strSheet = strSheet.replace("reSheetName", "Sheet" + index).replace("reIndex", index).replace("reOrder", order + "").replace("reStatus", status + "");
        JSONObject bson = JSONObject.parseObject(strSheet);
        GridRecordDataModel model = new GridRecordDataModel();
        model.setBlock_id("fblock");
        model.setRow_col("5_5");
        model.setIndex(index);
        model.setIs_delete(0);
        model.setJson_data(bson);
        model.setStatus(status);
        model.setOrder(order);
        model.setList_id(list_id);
        return model;
    }

    /**
     * ????????????????????????
     *
     * @param dbObject
     * @return
     */
    public String insert(GridRecordDataModel dbObject) {
        return recordDataInsertHandle.insert(dbObject);
    }

    public String insert(List<GridRecordDataModel> dbObject) {
        return recordDataInsertHandle.InsertIntoBatch(dbObject);
    }

    /**
     * ??????????????????,????????????
     *
     * @param gridKey
     * @param bson
     * @return
     */
    public String handleUpdate(String gridKey, Object bson) {
        StringBuilder _sb = new StringBuilder();
        if (bson instanceof List) {
            List<JSONObject> _list = (List<JSONObject>) bson;
            //JSONArray _list=(JSONArray)bson;
            //??????????????? 3.1???????????????v
            List<JSONObject> _vlist = new ArrayList<JSONObject>();
            for (JSONObject jsonObject : _list) {
                if (jsonObject.containsKey("t") && jsonObject.get("t").equals("v")) {
                    //???????????????
                    _vlist.add(jsonObject);
                } else {
                    //????????????
                    log.info("????????????--sb.append:chooseOperation");
                    _sb.append(chooseOperation(gridKey, jsonObject));
                }
            }
            if (_vlist.size() > 0) {
                //???????????????????????????
                if (_vlist.size() == 1) {
                    _sb.append(Operation_v(gridKey, _vlist.get(0)));
                } else {
                    _sb.append(Operation_v(gridKey, _vlist));
                }
            }
        } else if (bson instanceof JSONObject) {
            log.info("bson instanceof BasicDBObject--bson");
            _sb.append(chooseOperation(gridKey, (JSONObject) bson));
        }
        return _sb.toString();
    }

    //??????????????????
    private String chooseOperation(String gridKey, JSONObject bson) {
        if (bson.containsKey("t")) {
            String _result = "";
            if (SheetOperationEnum.contains(bson.get("t").toString())) {
                SheetOperationEnum _e = SheetOperationEnum.valueOf(bson.get("t").toString());

                switch (_e) {
                    case c:
                        //3.9	???????????? ???????????????
                        _result = Operation_c(gridKey, bson);
                        break;
                    case v:
                        //3.1	???????????????v  gzip ??????
                        _result = Operation_v(gridKey, bson);
                        break;
                    case cg:
                        //3.2   config??????cg ???????????????
                        _result = Operation_cg(gridKey, bson);
                        break;
                    case all:
                        //3.3 ???????????? ???????????????
                        _result = Operation_all(gridKey, bson);
                        break;
                    case fc:
                        //3.4.1 ???????????? ???????????????
                        _result = Operation_fc(gridKey, bson);
                        break;
                    case f:
                        //3.6.1 ?????????????????? ???????????????
                        _result = Operation_f(gridKey, bson);
                        break;
                    case fsc:
                        //3.6.2	????????? ???????????????
                        _result = Operation_fsc(gridKey, bson);
                        break;
                    case fsr:
                        //3.6.3	???????????? ???????????????
                        _result = Operation_fsr(gridKey, bson);
                        break;
                    case drc:
                        //3.5.1 ???????????????   gzip ??????
                        _result = Operation_drc(gridKey, bson);
                        break;
                    case arc:
                        //3.5.2	???????????????  gzip ??????
                        _result = Operation_arc(gridKey, bson);
                        break;
                    case sha:
                        //3.7.1	??????sha  sheet??????  gzip ??????
                        _result = Operation_sha(gridKey, bson);
                        break;
                    case shc:
                        //3.7.2	??????shc ??????
                        _result = Operation_shc(gridKey, bson);
                        break;
                    case shd:
                        //3.7.3	??????shd ??????
                        _result = Operation_shd(gridKey, bson);
                        break;
                    case shr:
                        //3.7.4	??????shr ??????
                        _result = Operation_shr(gridKey, bson);
                        break;
                    case shs:
                        //3.7.5	??????shs ??????
                        _result = Operation_shs(gridKey, bson);
                        break;
                    case sh:
                        //3.8.1	??????    3.8	sheet??????sh  ??????
                        _result = Operation_sh(gridKey, bson);
                        break;
                    case na:
                        //3.10.1	???????????? ?????? ?????????
                        _result = Operation_na(gridKey, bson);
                        break;
                    case thumb:
                        //3.10.2	?????????    ???????????????
                        _result = Operation_thumb(gridKey, bson);
                        break;
                    case rv:
                        //?????????????????????
                        _result = getIndexRvForThread(gridKey, bson);
                        break;
                    case shre:
                        _result = Operation_shre(gridKey, bson);
                        break;
                    case mv:
                        break;
                    default:
                        _result = "?????????????????????" + JsonUtil.toJson(bson);
                        break;
                }
            } else {
                _result = "?????????????????????" + JsonUtil.toJson(bson);
            }
            return _result;
        } else {
            return "???????????????" + JsonUtil.toJson(bson);
        }
    }

    //3.8.1	??????    3.8	sheet??????sh
    /*
    * 	?????????i?????????sheet????????????hide?????????v???????????????status??????0??????????????????1???
    * 	????????????????????????index??????cur???sheet???status?????????1
    *     [{"t":"sh","i":0,"v":1,"op":"hide","cur":1}]
          [{"t":"sh","i":0,"v":0,"op":"hide"}]
    *     ?????????where index=i set hide=v???status=0
    *           where index=cur set status=1
    * ???????????????where status=1 set status=0         ????????????status??????1???????????? 0
    *           where index=i set hide=v???status=1  ???i?????????index???status????????? 1
    **/
    private String Operation_sh(String gridKey, JSONObject bson) {
        try {
            String i = bson.get("i").toString();//??????sheet???index???
            String op = bson.get("op").toString();// 	??????????????????hide???show???
            Integer v = 0;  //??????hide???1???????????????0?????????????????????
            String cur = null; //	???????????????????????????cur???sheet???????????????
            if (bson.containsKey("v") && bson.get("v") != null) {
                v = Integer.parseInt(bson.get("v").toString());
            }
            if (bson.containsKey("cur") && bson.get("cur") != null) {
                cur = bson.get("cur").toString();
            }
            //1?????????????????????
            List<JSONObject> _dbObject = recordSelectHandle.getBlocksByGridKey(gridKey, true);
            if (_dbObject == null) {
                return "gridKey=" + gridKey + "????????????????????????";
            }
            //2??????????????????sheet?????????
            Integer _sheetPosition = JfGridFileUtil.getSheetPositionByIndex(_dbObject, i);
            if (_sheetPosition == null) {
                return "index=" + i + "???sheet?????????";
            }
            GridRecordDataModel model = new GridRecordDataModel();
            boolean _result = false;
            if (op.equals("hide")) {
                //??????
                //??????i???????????????????????????status=0
                model.setBlock_id(JfGridConfigModel.FirstBlockID);
                model.setList_id(gridKey);
                _result = recordDataUpdataHandle.updateDataMsgHide(model, v, i, cur);
            } else {
                //????????????
                //????????????status=0
                model.setBlock_id(JfGridConfigModel.FirstBlockID);
                model.setList_id(gridKey);
                _result = recordDataUpdataHandle.updateDataMsgNoHide(model, v, i);
            }
            if (!_result) {
                return "????????????";
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.7.5	??????shs
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_shs(String gridKey, JSONObject bson) {
        try {
            if (!bson.containsKey("v")) {
                return "????????????";
            }
            //??????Sheet????????????????????????sheet???index
            String i = bson.get("v").toString();
            //1?????????????????????
            List<JSONObject> _dbObject = recordSelectHandle.getBlocksByGridKey(gridKey, true);
            if (_dbObject == null) {
                return "gridKey=" + gridKey + "????????????????????????";
            }
            //2??????????????????sheet?????????
            Integer _sheetPosition = JfGridFileUtil.getSheetPositionByIndex(_dbObject, i);
            if (_sheetPosition == null) {
                return "index=" + i + "???sheet?????????";
            }
            GridRecordDataModel model = new GridRecordDataModel();
            model.setBlock_id(JfGridConfigModel.FirstBlockID);
            model.setIndex(i);
            model.setList_id(gridKey);
            boolean _result = recordDataUpdataHandle.updateDataStatus(model);
            if (!_result) {
                return "????????????";
            }

        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.7.4	??????shr
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_shr(String gridKey, JSONObject bson) {
        try {
            if (!bson.containsKey("v")) {
                return "????????????";
            }
            JSONObject _v = bson.getJSONObject("v");
            if (_v != null && _v.keySet().size() > 0) {
                //1?????????????????????
                List<JSONObject> _dbObject = recordSelectHandle.getBlocksByGridKey(gridKey, false);
                if (_dbObject == null) {
                    return "gridKey=" + gridKey + "????????????????????????";
                }

                List<GridRecordDataModel> models = new ArrayList<>();
                for (String _index : _v.keySet()) {
                    try {
                        // _index ????????????index???
                        String _i = _v.get(_index).toString();//????????????order???
                        if (_i != null) {
                            GridRecordDataModel model = new GridRecordDataModel();
                            model.setList_id(gridKey);
                            model.setBlock_id(JfGridConfigModel.FirstBlockID);
                            model.setOrder(Integer.valueOf(_i));
                            model.setIndex(_index);
                            models.add(model);
                        }
                    } catch (Exception ex) {
                        log.error(ex.toString());
                    }
                }

                if (models.size() > 0) {
                    boolean _result = recordDataUpdataHandle.batchUpdateForNoJsonbData(models);
                    if (!_result) {
                        return "????????????";
                    }
                }
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.7.3	??????shd
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_shd(String gridKey, JSONObject bson) {
        try {
            String deleIndex = null;//	???????????????sheet??????
            if (bson.containsKey("v")) {
                JSONObject _v = bson.getJSONObject("v");
                if (_v.containsKey("deleIndex")) {
                    deleIndex = _v.get("deleIndex").toString();
                }
            }
            if (deleIndex == null) {
                return "????????????";
            }

            //1?????????????????????
            List<JSONObject> _dbObject = recordSelectHandle.getBlocksByGridKey(gridKey, false);
            if (_dbObject == null) {
                return "gridKey=" + gridKey + "????????????????????????";
            }
            //2??????????????????sheet?????????
            GridRecordDataModel model = new GridRecordDataModel();
            model.setIndex(deleIndex);
            model.setList_id(gridKey);
            model.setIs_delete(1);
            boolean result = recordDelHandle.updateDataForReDel(model);
            if (!result) {
                return "????????????";
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.7.2	??????shc
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_shc(String gridKey, JSONObject bson) {
        try {
            String i = bson.get("i").toString();//	??????sheet?????????
            String copyindex = null;//????????????
            String name = null;
            if (bson.containsKey("v")) {
                JSONObject _v = bson.getJSONObject("v");
                if (_v.containsKey("copyindex")) {
                    copyindex = _v.get("copyindex").toString();
                }
                if (_v.containsKey("name")) {
                    name = (String) _v.get("name");
                }
            }
            if (copyindex == null) {
                return "????????????";
            }
            //1?????????????????????
            List<JSONObject> _dbObjects = recordSelectHandle.getBlockAllByGridKey(gridKey, copyindex);
            if (_dbObjects == null) {
                return "gridKey=" + gridKey + "????????????????????????";
            }

            for (JSONObject _dbObject : _dbObjects) {
                if (_dbObject.containsKey("id")) {
                    _dbObject.remove("id");
                }
                JSONObject jsondata = _dbObject.getJSONObject("json_data");
                if (jsondata.containsKey("name")) {
                    jsondata.put("name", name);
                    _dbObject.put("json_data", jsondata);
                }

                _dbObject.put("index", i);//??????sheet?????????
                _dbObject.put("status", 0);
            }

            String _mongodbKey = recordDataInsertHandle.InsertBatchDb(_dbObjects);
            if (_mongodbKey == null) {
                return "????????????";
            }

        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.7.1	??????sha  sheet??????
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_sha(String gridKey, JSONObject bson) {
        try {
            //Integer i=Integer.parseInt(bson.get("i").toString());// ??????sheet???index???,?????????null
            JSONObject v = bson.getJSONObject("v");   //???????????????
            log.info("Operation_sha--v:" + v);
            String index = null;// v???Index??????
            if (v.containsKey("index")) {
                index = "" + v.get("index").toString();
            }
            if (index == null) {
                return "index?????????null";
            }
            log.info("Operation_sha---" + index);
            //1?????????????????????
            List<JSONObject> _dbObject = recordSelectHandle.getBlocksByGridKey(gridKey.toString(), false);
            log.info("getIndexByGridKey---" + _dbObject);
            if (_dbObject == null) {
                return "gridKey=" + gridKey + "????????????????????????";
            }
            //2??????????????????sheet?????????
            Integer _sheetPosition = JfGridFileUtil.getSheetPositionByIndex(_dbObject, index);
            log.info("_sheetPosition--" + _sheetPosition);
            if (_sheetPosition != null) {
                return "index=" + index + "???sheet????????????";
            }
            GridRecordDataModel model = new GridRecordDataModel();
            model.setList_id(gridKey);
            model.setBlock_id(JfGridConfigModel.FirstBlockID);
            model.setIndex(index);
            model.setStatus(0);
            model.setOrder(Integer.valueOf(v.get("order").toString()));
            v.remove("list_id");
            v.remove("block_id");
            v.remove("index");
            v.remove("status");
            v.remove("order");
            model.setJson_data(v);
            //GzipHandle.toCompressBySheet(v);
            String _mongodbKey = recordDataInsertHandle.insert(model);
            if (_mongodbKey == null) {
                return "????????????";
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.5.2 ???????????????
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_arc(String gridKey, JSONObject bson) {
        try {
            String i = bson.get("i").toString();//	??????sheet???index???
            String rc = bson.get("rc").toString();   //??????????????????????????????r????????????c?????????
            Integer index = null;//  		?????????????????????????????????
            Integer len = null;// 		????????????????????????
            JSONArray data = null;// 	???????????????????????????
            JSONObject mc = null;//     	????????????????????????????????????
            String direction = null;//??????
            if (bson.get("v") != null && bson instanceof JSON) {
                JSONObject _v = bson.getJSONObject("v");
                if (_v.containsKey("index")) {
                    index = Integer.parseInt(_v.get("index").toString());
                }
                if (_v.containsKey("len")) {
                    len = Integer.parseInt(_v.get("len").toString());
                }
                if (_v.containsKey("data") && _v.get("data") instanceof JSONArray) {
                    data = _v.getJSONArray("data");
                }
                if (_v.containsKey("mc")) {
                    mc = _v.getJSONObject("mc");
                }
                if (_v.containsKey("direction")) {
                    direction = _v.get("direction").toString().trim();
                }

            }
            if (index == null || len == null) {
                return "????????????";
            }

            //1?????????????????????
            List<String> mongodbKeys = new ArrayList<String>();//mongodb???key???????????????
            JSONObject _dbObject = recordSelectHandle.getBlockMergeByGridKey(gridKey, i, mongodbKeys);
            if (_dbObject == null) {
                return "list_id=" + gridKey + ",index=" + i + "???sheet?????????";
                //return "gridKey="+gridKey+"????????????????????????";
            }

            JSONObject json_data = JfGridFileUtil.getJSONObjectByIndex(_dbObject, "json_data");
            Integer _column = JfGridFileUtil.getIntegerByIndex(json_data, "column"),
                    _row = JfGridFileUtil.getIntegerByIndex(json_data, "row");

            //??????????????????
            JSONArray _celldatas = JfGridFileUtil.getSheetByIndex(_dbObject);
            if (_celldatas != null) {
                for (int x = _celldatas.size() - 1; x >= 0; x--) {
                    JSONObject _cell = _celldatas.getJSONObject(x);
                    Integer _r = Integer.parseInt(_cell.get("r").toString());
                    Integer _c = Integer.parseInt(_cell.get("c").toString());
                    //??????????????????
                    if (rc.equals("r")) {
                        //?????????
                        if ("lefttop".equals(direction)) {
                            if (_r >= (index)) {
                                //??????????????????+??????
                                _celldatas.getJSONObject(x).put("r", _r + len);
                            }
                        } else {
                            if (_r > (index)) {
                                //??????????????????+??????
                                _celldatas.getJSONObject(x).put("r", _r + len);
                            }
                        }
                    } else if (rc.equals("c")) {
                        //?????????
                        //?????????
                        if ("lefttop".equals(direction)) {
                            if (_c >= (index)) {
                                //??????????????????+??????
                                _celldatas.getJSONObject(x).put("c", _c + len);
                            }
                        } else {
                            if (_c > (index)) {
                                //??????????????????+??????
                                _celldatas.getJSONObject(x).put("c", _c + len);
                            }
                        }
                    }
                }
                //??????????????????
                if (data != null) {
                    if (data.size() > 0) {
                        List<Object> _addList = new ArrayList<Object>();
                        if (rc.equals("r")) {
                            //?????????
                            for (int _x = 0; _x < data.size(); _x++) {
                                if (data.get(_x) != null && data.get(_x) instanceof List) {
                                    List _b = (List) data.get(_x);
                                    for (int _x1 = 0; _x1 < _b.size(); _x1++) {
                                        if (_b.get(_x1) != null) {
                                            JSONObject _m = new JSONObject();
                                            _m.put("r", _x + index);
                                            _m.put("c", _x1);
                                            _m.put("v", _b.get(_x1));
                                            _addList.add(_m);
                                        }
                                    }
                                }
                            }
                        } else if (rc.equals("c")) {
                            //?????????
                            for (int _x = 0; _x < data.size(); _x++) {
                                if (data.get(_x) != null && data.get(_x) instanceof List) {
                                    List _b = (List) data.get(_x);
                                    for (int _x1 = 0; _x1 < _b.size(); _x1++) {
                                        if (_b.get(_x1) != null) {
                                            JSONObject _m = new JSONObject();
                                            _m.put("r", _x);
                                            _m.put("c", _x1 + index);
                                            _m.put("v", _b.get(_x1));
                                            _addList.add(_m);
                                        }
                                    }
                                }
                            }
                        }
                        _celldatas.addAll(_addList);
                    }
                }
                //????????????config??????merge??????
                if (mc != null) {
                    if (json_data.containsKey("config")) {
                        json_data.getJSONObject("config").put("merge", mc);
                    } else {
                        JSONObject _d = new JSONObject();
                        _d.put("merge", mc);
                        json_data.put("config", _d);
                    }
                    //???mc?????????????????????jfgridfile[1].data??????????????????(mc)?????????
                    drc_arc_handle_mc(mc, _celldatas);
                }

                if (rc.equals("r")) {
                    json_data.put("row", _row + len);
                } else {
                    json_data.put("column", _column + len);
                }
                _dbObject.put("json_data", json_data);
                //????????????????????????????????????????????????
                String rowCol = recordSelectHandle.getFirstBlockRowColByGridKey(gridKey, i);
                List<JSONObject> blocks = JfGridConfigModel.toDataSplit(rowCol, _dbObject);
                boolean _result = recordDataUpdataHandle.updateMulti2(blocks, mongodbKeys);
                //boolean _result=false;
                if (!_result) {
                    return "????????????";
                }
            }


        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.5.1 ???????????????
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_drc(String gridKey, JSONObject bson) {
        try {
            //??????sheet???index???
            String i = bson.get("i").toString();
            //??????????????????????????????r????????????c?????????
            String rc = bson.get("rc").toString();
            //?????????????????????????????????
            Integer index = null;
            //????????????????????????
            Integer len = null;
            //????????????????????????????????????
            JSONObject mc = null;
            JSONArray borderInfo = null;
            if (bson.get("v") != null && bson instanceof JSON) {
                JSONObject _v = bson.getJSONObject("v");
                if (_v.containsKey("index")) {
                    index = Integer.parseInt(_v.get("index").toString());
                }
                if (_v.containsKey("len")) {
                    len = Integer.parseInt(_v.get("len").toString());
                }
                if (_v.containsKey("mc")) {
                    mc = _v.getJSONObject("mc");
                }
                if (_v.containsKey("borderInfo")) {
                    borderInfo = _v.getJSONArray("borderInfo");
                }
            }
            if (index == null || len == null) {
                return "????????????";
            }
            //1?????????????????????
            //?????????ids???????????????
            List<String> ids = new ArrayList<String>();
            JSONObject _dbObject = recordSelectHandle.getBlockMergeByGridKey(gridKey, i, ids);
            if (_dbObject == null) {
                return "list_id=" + gridKey + ",index=" + i + "???sheet?????????";
                //return "gridKey="+gridKey+"????????????????????????";
            }

            JSONObject json_data = JfGridFileUtil.getJSONObjectByIndex(_dbObject, "json_data");

            //??????????????????
            JSONArray _celldatas = JfGridFileUtil.getSheetByIndex(_dbObject);
            if (_celldatas != null) {
                for (int x = _celldatas.size() - 1; x >= 0; x--) {
                    JSONObject _cell = _celldatas.getJSONObject(x);
                    Integer _r = Integer.parseInt(_cell.get("r").toString());
                    Integer _c = Integer.parseInt(_cell.get("c").toString());
                    //??????????????????
                    if (rc.equals("r")) {
                        //?????????
                        if (_r >= index && _r <= (index + len - 1)) {
                            //???????????????
                            _celldatas.remove(x);
                        }
                        if (_r >= (index + len)) {
                            //??????????????????-??????
                            _celldatas.getJSONObject(x).put("r", _r - len);
                        }
                    } else if (rc.equals("c")) {
                        //?????????
                        if (_c >= index && _c <= (index + len - 1)) {
                            //???????????????
                            _celldatas.remove(x);
                        }
                        if (_c >= (index + len)) {
                            //??????????????????-??????
                            _celldatas.getJSONObject(x).put("c", _c - len);
                        }
                    }
                }

                // ??????

                //????????????config??????merge??????
                JSONObject _d = new JSONObject();
                if (mc != null) {
                    if (json_data.containsKey("config")) {
                        json_data.getJSONObject("config").put("merge", mc);
                    } else {
                        _d.put("merge", mc);
                        json_data.put("config", _d);
                    }
                    //???mc?????????????????????jfgridfile[1].data??????????????????(mc)?????????
                    drc_arc_handle_mc(mc, _celldatas);
                }

                //????????????config??????merge??????
                if (borderInfo != null) {
                    if (json_data.containsKey("config")) {
                        json_data.getJSONObject("config").put("borderInfo", borderInfo);
                    } else {
                        _d.put("borderInfo", mc);
                        json_data.put("config", _d);
                    }
                   /* //???mc?????????????????????jfgridfile[1].data??????????????????(mc)?????????
                    drc_arc_handle_mc(mc,_celldatas);*/
                }

                /*if(rc.equals("r")){
                	json_data.put("row",_row-len);
                }else{
                	json_data.put("column",_column-len);
                }*/
                _dbObject.put("json_data", json_data);
                //????????????????????????????????????????????????
                String rowCol = recordSelectHandle.getFirstBlockRowColByGridKey(gridKey, i);
                List<JSONObject> blocks = JfGridConfigModel.toDataSplit(rowCol, _dbObject);
                boolean _result = recordDataUpdataHandle.updateMulti2(blocks, ids);
                if (!_result) {
                    return "????????????";
                }
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return ex.getMessage();
        }
        return "";
    }

    /**
     * ???mc??????data?????????
     *
     * @param mc
     * @param _celldatas
     */
    private void drc_arc_handle_mc(JSONObject mc, JSONArray _celldatas) {
        List<ConfigMergeModel> _list = ConfigMergeModel.getListByDBObject(mc);
        for (int x = _celldatas.size() - 1; x >= 0; x--) {
            try {
                JSONObject _cell = _celldatas.getJSONObject(x);
                Integer _r = Integer.parseInt(_cell.get("r").toString());
                Integer _c = Integer.parseInt(_cell.get("c").toString());
                for (ConfigMergeModel _cmModel : _list) {
                    if (_cmModel.isRange(_r, _c)) {
                        if (_cell.containsKey("v")) {
                            JSONObject _v = _cell.getJSONObject("v");
                            if (_v.containsKey("mc")) {
                                JSONObject _mc = _v.getJSONObject("mc");
                                _mc.put("r", _cmModel.getR());
                                _mc.put("c", _cmModel.getC());
                            }
                        }
                        break;
                    }
                }
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }
    }

    //3.3 ????????????
    private String Operation_all(String gridKey, JSONObject bson) {
        /*
        {
          "t": "all",
          "i": 3,
          "v": "{\"pivot_select_save\":{\"left\":105,\"width\":73,\"top\":0,\"height\":19,\"left_move\":105,\"width_move\":600,\"top_move\":0,\"height_move\":79,\"row\":[0,3],\"column\":[1,6],\"row_focus\":0,\"column_focus\":1},\"pivotDataSheetIndex\":0,\"column\":[{\"index\":0,\"name\":\"?????????\",\"fullname\":\"?????????\"}],\"row\":[],\"filter\":[],\"values\":[{\"index\":1,\"name\":\"??????????????????\",\"fullname\":\"??????:??????????????????\",\"sumtype\":\"SUM\",\"nameindex\":0},{\"index\":2,\"name\":\"??????NBV??????\",\"fullname\":\"??????:??????NBV??????\",\"sumtype\":\"SUM\",\"nameindex\":0},{\"index\":3,\"name\":\"????????????\",\"fullname\":\"??????:????????????\",\"sumtype\":\"SUM\",\"nameindex\":0},{\"index\":4,\"name\":\"????????????\",\"fullname\":\"??????:????????????\",\"sumtype\":\"SUM\",\"nameindex\":0},{\"index\":5,\"name\":\"NBV\",\"fullname\":\"??????:NBV\",\"sumtype\":\"SUM\",\"nameindex\":0}],\"showType\":\"column\",\"drawPivotTable\":false,\"pivotTableBoundary\":[3,17]}",
          "k": "pivotTable",
          "s": true
        }
        * */
        try {
            log.info("start---Operation_all" + bson.toString(SerializerFeature.WriteMapNullValue));
            String i = bson.get("i").toString();//	??????sheet???index???
            String k = bson.get("k").toString();   //	???????????????key-value??????key
            String s = "true";   //	?????????true???v???????????????????????????????????????????????????
            log.info("Operation_all:i:" + i + "k:" + k);
            Object _v = null;//?????????????????? (????????????????????????????????????)
            if (bson.get("v") != null) {
                if (bson.get("v") instanceof String) {
                    log.info("bson.get('v')+string+true");
                    try {
                        _v = bson.get("v").toString();
                    } catch (Exception e) {
                        log.error("DBObject---error");
                        _v = bson.get("v");
                    }

                } else {
                    log.info("bson.get('v')+false");
                    _v = bson.get("v");
                    s = "false";
                    log.info("Operation_all:_v:false:" + s);
                }
            } else {
                s = "true";
                _v = null;
            }
            //???????????????????????????????????????s???false
            if (_v != null) {
                if (_v.toString().indexOf("{\"pivot_select_save\":") > -1) {
                    s = "false";
                }
            }
            log.info("Operation_all:start+getConfigByGridKey:_v:" + _v);
            //1?????????????????????
            JSONObject _dbObject = recordSelectHandle.getConfigByGridKey(gridKey, i);
            if (_dbObject == null) {
                return "list_id=" + gridKey + ",index=" + i + "???sheet?????????";
                //return "gridKey="+gridKey+"????????????????????????";
            }

            //Query query = new Query();
            //query.addCriteria(Criteria.where("list_id").is(gridKey).and("index").is(i).and("block_id").is(JfGridConfigModel.FirstBlockID));
            JSONObject query = getQuery(gridKey, i, JfGridConfigModel.FirstBlockID);

            boolean _result = false;
            String keyName = k;
            log.info("start----update+s:" + s);
            if (s.equals("true")) {
                if (null == _v) {

                } else {
                    _v = "\"" + _v + "\"";
                }
                _result = recordDataUpdataHandle.updateCellDataListTxtValue(query, keyName, null, _v);
            } else {
                try {
                    //JSONObject _vdb=JSONObject.parseObject(_v.toString());
                    //_result = recordDataUpdataHandle.updateCellDataListValue(query, keyName, null, _vdb);
                    _result = recordDataUpdataHandle.updateCellDataListValue(query, keyName, null, _v);
                    //update.set("jfgridfile."+_sheetPosition+"."+k,_vdb);
                } catch (Exception ex) {
                    log.error("Operation_all--erorr:" + ex.toString());
                    _v = "\"" + _v + "\"";
                    _result = recordDataUpdataHandle.updateCellDataListTxtValue(query, keyName, null, _v);
                    //update.set("jfgridfile."+_sheetPosition+"."+k,_v);
                }
            }
            log.info("updateOne--start");
            if (!_result) {
                return "????????????";
            }
        } catch (Exception ex) {
            log.error("Operation_all--err--all:" + ex.getMessage());
        }
        return "";
    }

    /**
     * 3.4.1 ????????????
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_fc(String gridKey, JSONObject bson) {
        try {
            //??????sheet???index???
            String i = bson.get("i").toString();
            //???????????????????????????????????????????????????????????????????????????????????????  2018-11-28 ????????????????????????????????????
            JSONObject v = bson.getJSONObject("v");
            //if (bson.get("v") instanceof String) {
            //    v = bson.get("v").toString();
            //} else {
            //    v = bson.get("v");
            //}

            //????????????,add????????????update????????????del?????????
            String op = bson.get("op").toString();
            //?????????????????????????????????
            String pos = bson.get("pos").toString();

            //1?????????????????????
            JSONObject _dbObject = recordSelectHandle.getConfigByGridKey(gridKey, i);
            if (_dbObject == null) {
                return "list_id=" + gridKey + ",index=" + i + "???sheet?????????";
                //return "gridKey="+gridKey+"????????????????????????";
            }

            //Query query = new Query();
            //query.addCriteria(Criteria.where("list_id").is(gridKey).and("index").is(i).and("block_id").is(JfGridConfigModel.FirstBlockID));
            JSONObject query = getQuery(gridKey, i, JfGridConfigModel.FirstBlockID);

            boolean _result = false;
            Object calcChain = JfGridFileUtil.getObjectByIndex(_dbObject, "calcchain");
            if (calcChain == null) {
                //????????? (???????????????)
                if (op.equals("add")) {

                    //update.set("calcChain",_dlist);//??????
                    _result = recordDataUpdataHandle.updateJsonbForSetNull(query, "calcChain", v, 0);
                }
            } else {
                //??????
                if (op.equals("add")) {
                    _result = recordDataUpdataHandle.updateJsonbForElementInsert(query, "calcChain", v, 0);
                } else if (op.equals("update")) {
                    //update.set("calcChain."+pos,v);//??????
                    _result = recordDataUpdataHandle.updateCellDataListValue(query, "calcChain", pos, v);
                } else if (op.equals("del")) {
                    if (calcChain instanceof List) {
                        List<JSONObject> _list = (List<JSONObject>) calcChain;
                        Integer size = Integer.valueOf(pos);
                        if (size <= _list.size()) {
                            int listindex = size;
                            _list.remove(listindex);
                            //update.set("calcChain",calcChain);//????????????
                            _result = recordDataUpdataHandle.updateCellDataListValue(query, "calcChain", null, calcChain);
                        }
                    }
                }
            }
            if (!_result) {
                return "????????????";
            }

        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.6.1 ??????????????????
     * ??????jfgridfile[i].filter = { pos : v }??? v????????????JSON?????????????????????
     * filter?????????????????????
     * key??????????????????????????????????????????????????????
     * v????????????json??????????????????filter?????????????????????????????????
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_f(String gridKey, JSONObject bson) {
        try {
            //??????sheet???index???
            String i = bson.get("i").toString();
            //???????????????????????????????????????????????????????????????????????????????????????
            String v = bson.get("v").toString();
            //????????????upOrAdd???????????????????????????????????????del?????????
            String op = bson.get("op").toString();
            //?????????????????????option??????
            String pos = bson.get("pos").toString();

            //1?????????????????????
            JSONObject _dbObject = recordSelectHandle.getConfigByGridKey(gridKey, i);
            if (_dbObject == null) {
                return "list_id=" + gridKey + ",index=" + i + "???sheet?????????";
                //return "gridKey="+gridKey+"????????????????????????";
            }

            //Query query = new Query();
            //query.addCriteria(Criteria.where("list_id").is(gridKey).and("index").is(i).and("block_id").is(JfGridConfigModel.FirstBlockID));
            JSONObject query = getQuery(gridKey, i, JfGridConfigModel.FirstBlockID);

            boolean _result = false;
            //???????????????????????????????????????
            JSONObject filter = JfGridFileUtil.getJSONObjectByIndex(_dbObject, "filter");
            if (op.equals("upOrAdd")) {
                // update.set("filter."+pos,v);//??????

                _result = recordDataUpdataHandle.updateCellDataListValue(query, "filter", pos, v);
            } else if (op.equals("del")) {
                if (filter != null) {
                    //update.unset("filter."+pos);//??????
                    _result = recordDataUpdataHandle.updateCellDataListValue(query, "filter", pos, v);
                }
            }
            if (!_result) {
                return "????????????";
            }

        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.6.2	?????????
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_fsc(String gridKey, JSONObject bson) {
        try {
            //??????sheet???index???
            String i = bson.get("i").toString();

            //1?????????????????????
            JSONObject _dbObject = recordSelectHandle.getConfigByGridKey(gridKey, i);
            if (_dbObject == null) {
                return "list_id=" + gridKey + ",index=" + i + "???sheet?????????";
                //return "gridKey="+gridKey+"????????????????????????";
            }

            //Query query = new Query();
            //query.addCriteria(Criteria.where("list_id").is(gridKey).and("index").is(i).and("block_id").is(JfGridConfigModel.FirstBlockID));
            JSONObject query = getQuery(gridKey, i, JfGridConfigModel.FirstBlockID);

            //???????????????????????????????????????
            /*DBObject v=new BasicDBObject();
            update.set("filter",v);//??????
            update.set("filter_select",v);//??????
            */
            String word = "\"filter\":null,\"filter_select\":null";
            boolean _result = recordDataUpdataHandle.rmJsonbDataForEmpty(query, word);
            if (!_result) {
                return "????????????";
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.6.3	????????????
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_fsr(String gridKey, JSONObject bson) {
        try {
            //??????sheet???index???
            String i = bson.get("i").toString();
            Object filter = null;
            if (bson.get("filter") != null) {
                filter = (Object) bson.get("filter");
            } else {
                filter = new JSONObject();
            }

            Object filter_select = null;//
            if (bson.get("filter_select") != null) {
                filter_select = (Object) bson.get("filter_select");
            } else {
                filter_select = new JSONArray();
            }

            //1?????????????????????
            JSONObject _dbObject = recordSelectHandle.getConfigByGridKey(gridKey, i);
            if (_dbObject == null) {
                return "list_id=" + gridKey + ",index=" + i + "???sheet?????????";
                //return "gridKey="+gridKey+"????????????????????????";
            }

            //Query query = new Query();
            //query.addCriteria(Criteria.where("list_id").is(gridKey).and("index").is(i).and("block_id").is(JfGridConfigModel.FirstBlockID));
            JSONObject query = getQuery(gridKey, i, JfGridConfigModel.FirstBlockID);

            JSONObject db = new JSONObject();
            db.put("filter", filter);
            db.put("filter_select", filter_select);
            boolean _result = recordDataUpdataHandle.updateJsonbDataForKeys(query, db);
            if (!_result) {
                return "????????????";
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.2   config??????cg
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_cg(String gridKey, JSONObject bson) {
        try {
            //??????sheet???index???
            String i = bson.get("i").toString();
            String k = bson.get("k").toString();

            JSONObject _v = null;//??????????????????
            if (bson.get("v") != null) {
                _v = bson.getJSONObject("v");
            }
            if (_v == null) {
                //?????????????????????
                return "";
            }

            //1?????????????????????
            JSONObject _dbObject = recordSelectHandle.getConfigByGridKey(gridKey, i);
            if (_dbObject == null) {
                return "list_id=" + gridKey + ",index=" + i + "???sheet?????????";
                //return "gridKey="+gridKey+"????????????????????????";
            }
            //??????_v???????????????null?????????????????????
            boolean flag = false;
            String keys = "";
            if (_v.keySet().size() != 0) {
                for (String key : _v.keySet()) {
                    if (_v.get(key) == null) {
                        keys = key;
                        flag = true;
                    }
                }
            } else {
                flag = true;
            }
            //Query query = new Query();
            //query.addCriteria(Criteria.where("list_id").is(gridKey).and("index").is(i).and("block_id").is(JfGridConfigModel.FirstBlockID));
            JSONObject query = getQuery(gridKey, i, JfGridConfigModel.FirstBlockID);

            JSONObject _config = JfGridFileUtil.getJSONObjectByIndex(_dbObject, "config");
            String keyName = "";
            boolean _result = false;
            if (_config != null) {
                if (flag) {
                    if ("".equals(keys)) {
                        keyName = "config," + k;
                    } else {
                        keyName = "config," + k + "," + keys;
                    }
                    _result = recordDataUpdataHandle.rmCellDataValue(query, keyName);
                    if (!_result) {
                        return "????????????";
                    }
                } else {
                    JSONObject _k = JfGridFileUtil.getObjectByObject(_config, k);
                    if (_k != null) {
                        //??????????????????
                        //_k.putAll(_v);
                        keyName = "config," + k;
                        //???jsonb????????????????????????????????????
                        _result = recordDataUpdataHandle.updateCellDataListValue(query, keyName, null, _v);
                        //update.set("jfgridfile."+_sheetPosition+".config."+k,_k);
                    } else {
                        //????????????
                        //update.set("config."+k,_v);
                        _result = recordDataUpdataHandle.updateJsonbForSetRootNull(query, "config," + k, _v, null, "\"config\":{\"" + k + "\":\"\"}");
                        //update.set("jfgridfile."+_sheetPosition+".config."+k,_v);
                    }
                    if (!_result) {
                        return "????????????";
                    }
                }
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.1	???????????????v  ?????????????????????,
     *
     * @param gridKey
     * @param dbObject
     * @return
     */
    private String Operation_v(String gridKey, List<JSONObject> dbObject) {
        try {
            int _count = dbObject.size();
            log.info("start---Operation_v--list" + dbObject);
            //???????????????
            HashMap<String, JSONObject> _existsBlock = new HashMap<String, JSONObject>(4);
            //???????????????
            HashMap<String, JSONObject> _noExistsBlock = new HashMap<String, JSONObject>(4);

            Map<String, String> rowColMap = new HashMap<>(2);

            for (int x = 0; x < _count; x++) {
                JSONObject bson = dbObject.get(x);
                String i = bson.get("i").toString();//	??????sheet???index???
                Integer r = Integer.parseInt(bson.get("r").toString());//	??????????????????
                Integer c = Integer.parseInt(bson.get("c").toString());//	??????????????????
                Object v = bson.get("v");  //	??????????????? v=null ???????????????

                //????????????
                String rowCol = null;
                if (rowColMap.containsKey(i)) {
                    rowCol = rowColMap.get(i);
                } else {
                    rowCol = recordSelectHandle.getFirstBlockRowColByGridKey(gridKey, i);
                    rowColMap.put(i, rowCol);
                }

                if (x == 0) {
                    //???????????????????????????sheet
                    //??????????????????????????????
                    Integer isHave = recordSelectHandle.getFirstBlockByGridKey(gridKey, i);
                    if (isHave == null || isHave == 0) {
                        return "list_id=" + gridKey + ",index=" + i + "???sheet?????????;";
                    }
                }
                //??????????????????????????????
                String block_id = JfGridConfigModel.getRange(r, c, rowCol);

                boolean isExists = false;
                JSONObject _dbObject = null;
                if (_existsBlock.containsKey(block_id)) {
                    //mongodb????????????????????????????????????
                    isExists = true;
                    _dbObject = _existsBlock.get(block_id);
                } else if (_noExistsBlock.containsKey(block_id)) {
                    //mongodb????????????
                    _dbObject = _noExistsBlock.get(block_id);
                } else {
                    //?????????????????????
                    //1?????????????????????????????????????????????sheet???
                    _dbObject = recordSelectHandle.getCelldataByGridKey(gridKey, i, block_id);
                    if (_dbObject == null) {
                        //???????????????????????????
                        //??????
                        JSONArray _celldata = new JSONArray();
                        //??????
                        JSONObject db = new JSONObject();
                        db.put("celldata", _celldata);
                        db.put("block_id", block_id);//??????sheet????????????
                        db.put("index", i); //??????sheet?????????
                        db.put("list_id", gridKey);//????????????
                        _noExistsBlock.put(block_id, db);
                        _dbObject = db;
                    } else {
                        //?????????
                        isExists = true;
                        _existsBlock.put(block_id, _dbObject);
                    }
                }
                //?????????????????????????????????????????????
                if (v != null) {
                    //??????/??????
                    JSONObject _v = new JSONObject();
                    _v.put("r", r);
                    _v.put("c", c);
                    _v.put("v", v);

                    if (isExists) {
                        //????????????
                        int _position = -1;//???????????????????????????
                        JSONArray _celldata = JfGridFileUtil.getSheetByIndex(_dbObject);
                        if (_celldata != null && _celldata.size() > 0) {
                            int _total = _celldata.size();
                            for (int y = 0; y < _total; y++) {
                                JSONObject _b = _celldata.getJSONObject(y);
                                if (_b.get("r").toString().equals(r + "") && _b.get("c").toString().equals(c + "")) {
                                    _b.put("v", v);
                                    _position = y;
                                    break;
                                }
                            }
                        }
                        if (_position == -1) {
                            assert _celldata != null;
                            _celldata.add(_v);
                        }
                    } else {
                        //????????????????????????????????????????????????????????????
                        JSONArray _celldata = JfGridFileUtil.getSheetByIndex(_dbObject);
                        _celldata.add(_v);
                    }
                } else {
                    //??????
                    if (isExists) {
                        //??????????????????
                        int _position = -1;//???????????????????????????
                        JSONArray _celldata = JfGridFileUtil.getSheetByIndex(_dbObject);
                        if (_celldata != null && _celldata.size() > 0) {
                            int _total = _celldata.size();
                            for (int y = 0; y < _total; y++) {
                                JSONObject _b = _celldata.getJSONObject(y);
                                if (_b.get("r").toString().equals(r + "") && _b.get("c").toString().equals(c + "")) {
                                    _position = y;
                                    break;
                                }
                            }
                        }
                        if (_position != -1) {
                            _celldata.remove(_position);
                        }
                    }
                }
            }
            //??????
            List<GridRecordDataModel> models = new ArrayList<>();
            List<String> block_ids = new ArrayList<>();
            if (_existsBlock.size() > 0) {
                for (String _block : _existsBlock.keySet()) {
                    block_ids.add(_block);
                    GridRecordDataModel model = new GridRecordDataModel();
                    JSONObject _bson = _existsBlock.get(_block);
                    JSONArray _celldata = JfGridFileUtil.getSheetByIndex(_bson);
                    JSONObject json_data = new JSONObject();
                    json_data.put("celldata", _celldata);
                    model.setJson_data(json_data);
                    model.setBlock_id(_block);
                    model.setIndex(_bson.get("index").toString());
                    model.setList_id(gridKey);
                    model.setStatus(0);
                    model.setIs_delete(0);
                    models.add(model);
                }
            }
            if (models.size() > 0) {
                boolean _result = recordDataUpdataHandle.batchUpdateCellDataValue(block_ids, models);
                if (!_result) {
                    return "????????????";
                }
            }
            if (_noExistsBlock.size() > 0) {
                for (JSONObject _d : _noExistsBlock.values()) {
                    GridRecordDataModel model = new GridRecordDataModel();
                    model.setBlock_id(_d.get("block_id").toString());
                    model.setIndex(_d.get("index").toString());
                    model.setList_id(_d.get("list_id").toString());
                    Object DB = _d.get("celldata");
                    JSONObject json_data = new JSONObject();
                    json_data.put("celldata", DB);
                    model.setJson_data(json_data);
                    model.setStatus(0);
                    model.setIs_delete(0);
                    models.add(model);
                }
                String _result = recordDataInsertHandle.InsertIntoBatch(models);
                if (_result == null) {
                    return "????????????";
                }
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.1	???????????????v
     *
     * @param gridKey
     * @param bson
     * @return
     */
    private String Operation_v(String gridKey, JSONObject bson) {
        if (GzipHandle.runGzip) {
            //????????????
            return "";
        }
        //???????????????
        try {
            log.info("start---Operation_v" + bson.toString());
            String i = bson.get("i").toString();//	??????sheet???index???
            Integer r = Integer.parseInt(bson.get("r").toString());//	??????????????????
            Integer c = Integer.parseInt(bson.get("c").toString());//	??????????????????
            Object v = bson.get("v");  //	??????????????? v=null ???????????????

            //??????????????????????????????
            Integer isHave = recordSelectHandle.getFirstBlockByGridKey(gridKey, i);
            log.info("isHave---Operation_v" + isHave);
            if (isHave == null || isHave == 0) {
                return "list_id=" + gridKey + ",index=" + i + "???sheet?????????";
            }

            //????????????
            String rowCol = recordSelectHandle.getFirstBlockRowColByGridKey(gridKey, i);
            //??????????????????????????????
            String block_id = JfGridConfigModel.getRange(r, c, rowCol);
            log.info("block_id---Operation_v" + block_id);
            //1?????????????????????????????????????????????sheet???
            JSONObject _dbObject = recordSelectHandle.getCelldataByGridKey(gridKey, i, block_id);
            if (_dbObject == null) {
                //return "list_id="+gridKey+",index="+i+"???sheet?????????";
                //return "list_id="+gridKey+"????????????????????????";
                //?????????????????????????????????
                if (v != null) {
                    //????????????
                    //?????????
                    JSONObject _v = new JSONObject();
                    _v.put("r", r);
                    _v.put("c", c);
                    _v.put("v", v);
                    //??????
                    JSONArray _celldata = new JSONArray();
                    _celldata.add(_v);
                    //??????
                    JSONObject db = new JSONObject();
                    db.put("celldata", _celldata);
                    GridRecordDataModel pg = new GridRecordDataModel();
                    pg.setBlock_id(block_id);
                    pg.setIndex(i);
                    pg.setList_id(gridKey);
                    pg.setJson_data(db);
                    pg.setStatus(0);
                    pg.setIs_delete(0);
                    //????????????
                    String _mongodbKey = recordDataInsertHandle.insert(pg);
                    if (_mongodbKey == null) {
                        return "????????????";
                    }
                }
            } else {
                //????????????????????????
                //3????????????????????????????????????
                int _position = -1;//???????????????????????????
                JSONObject _sourceDb = null;//???????????? (???????????????)
                //??????????????????
                //_dbObject=jfGridFileGetService.getCelldataByGridKey(gridKey,i,r,c);
                if (_dbObject != null) {
                    //???????????????????????????
                    //BasicDBList _celldata=JfGridFileUtil.getSheetByIndex(_dbObject,i);
                    JSONArray _celldata = JfGridFileUtil.getSheetByIndex(_dbObject);
                    if (_celldata != null && _celldata.size() > 0) {
                        int _total = _celldata.size();
                        for (int x = 0; x < _total; x++) {
                            JSONObject _b = _celldata.getJSONObject(x);
                            if (_b.get("r").toString().equals(r + "") && _b.get("c").toString().equals(c + "")) {
                                _position = x;
                                _sourceDb = _b;
                                break;
                            }
                        }
                    }
                }

                //Query query = new Query();
                //query.addCriteria(Criteria.where("list_id").is(gridKey).and("index").is(i).and("block_id").is(block_id));
                JSONObject query = getQuery(gridKey, i, block_id);

                boolean _result = false;
                if (v == null) {
                    if (_sourceDb != null) {
                        //???????????????null??????????????????
                        //update.pull("jfgridfile."+_sheetPosition+".celldata",_sourceDb);
                        String keyName = "celldata," + _position;
                        _result = recordDataUpdataHandle.rmCellDataValue(query, keyName);
                        if (!_result) {
                            return "????????????";
                        }
                    }
                } else {
                    if (_position != -1) {
                        //???????????????
                        //update.set("jfgridfile."+_sheetPosition+".celldata."+_position+".v",v);
                        //update.set("celldata."+_position+".v",v);
                        //???jsonb????????????????????????????????????
                        String pos = String.valueOf(_position);
                        _result = recordDataUpdataHandle.updateCellDataListValue(query, "celldata," + pos + ",v", null, v);
                    } else {
                        //?????????
                        JSONObject _db = new JSONObject();
                        _db.put("r", r);
                        _db.put("c", c);
                        _db.put("v", v);
                        //update.push("jfgridfile."+_sheetPosition+".celldata",_db);
                        //update.push("celldata",_db);
                        _result = recordDataUpdataHandle.updateJsonbForElementInsert(query, "celldata", _db, 0);
                    }
                    if (!_result) {
                        return "????????????";
                    }
                }
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.10.1	???????????? ?????? ?????????
     *
     * @param gridKey
     * @param bson
     * @return
     */
    public String Operation_na(String gridKey, JSONObject bson) {
        try {
            String v = null;// 	???????????????
            if (bson.containsKey("v")) {
                v = bson.get("v").toString().trim();
            }
            LuckySheetGridModel model = new LuckySheetGridModel();
            model.setList_id(gridKey);
            model.setGrid_name(v);

            //???????????????
            int i = 1;
            if (i == 0) {
                return "????????????";
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.10.2	?????????    ??????????????? ??? postgre
     *
     * @param gridKey
     * @param bson
     * @return
     */
    public String Operation_thumb(String gridKey, JSONObject bson) {
        try {
            log.info("Operation_thumb----start");
            String curindex = null;//???????????????????????????sheet
            String img = null;//	??????????????????????????????base64?????????
            if (bson.containsKey("img")) {
                img = bson.get("img").toString();
            }
            log.info("Operation_thumb----img" + img);
            if (bson.containsKey("curindex")) {
                curindex = bson.get("curindex").toString();
            }
            log.info("Operation_thumb----curindex" + curindex);
            if (curindex == null || img == null) {
                return "????????????";
            }

            //1?????????????????????
            List<JSONObject> _dbObject = recordSelectHandle.getBlocksByGridKey(gridKey, false);
            if (_dbObject == null) {
                return "gridKey=" + gridKey + "????????????????????????";
            }
            log.info("getSheetPositionByIndex--start");
            //2??????????????????sheet?????????
            Integer _sheetPosition = JfGridFileUtil.getSheetPositionByIndex(_dbObject, curindex);
            if (_sheetPosition == null) {
                return "index=" + curindex + "???sheet?????????";
            }
            //????????????status=0
            GridRecordDataModel model = new GridRecordDataModel();
            model.setBlock_id(JfGridConfigModel.FirstBlockID);
            model.setIndex(curindex);
            model.setList_id(gridKey);
            boolean _result = recordDataUpdataHandle.updateDataStatus(model);
            if (!_result) {
                return "????????????";
            }

            LuckySheetGridModel models = new LuckySheetGridModel();
            //model.setMongodbkey(gridKey.toString());
            models.setList_id(gridKey);
            models.setGrid_thumb(img.getBytes("UTF-8"));
            log.info("Operation_thumb---updateGridThumbByMongodbKey--start");

            //?????????????????????
            int i = 1;
            if (i == 0) {
                return "?????????????????????";
            }

        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * 3.9	????????????
     *
     * @param gridKey2
     * @param bson
     * @return
     */
    public String Operation_c(String gridKey2, JSONObject bson) {
        return Operation_c2(gridKey2, bson);
    }

    /**
     * ????????????
     *
     * @param gridKey2
     * @param bson
     * @return
     */
    private String Operation_c2(String gridKey2, JSONObject bson) {
        try {
            String i = bson.get("i").toString();//	??????sheet???index???
            String cid = bson.get("cid").toString();//	Chart?????????id
            String op = bson.get("op").toString();//??????????????????add???xy???wh???del???update???
            JSONObject v = null;
            if (bson.containsKey("v")) {
                v = bson.getJSONObject("v");
            }
            //1????????????????????????????????????
            JSONObject _dbObject = recordSelectHandle.getChartByGridKey(gridKey2, i);
            if (_dbObject == null) {
                return "list_id=" + gridKey2 + ",index=" + i + "???sheet?????????";
                //return "gridKey="+gridKey+"????????????????????????";
            }
            //???????????????????????????
            //Query query = new Query();
            //query.addCriteria(Criteria.where("list_id").is(gridKey2).and("index").is(i).and("block_id").is(JfGridConfigModel.FirstBlockID));
            JSONObject query = getQuery(gridKey2, i, JfGridConfigModel.FirstBlockID);

            //??????????????????????????????
            JSONObject chart = JfGridFileUtil.getJSONObjectByIndex(_dbObject, "chart");
            boolean _result = false;
            if (chart == null) {
                //????????? (???????????????)
                if (op.equals("add")) {
                    _result = recordDataUpdataHandle.updateJsonbForInsertNull(query, "chart", v, 0, "\"chart\":[]");
                }
            } else {
                //??????
                if (op.equals("add")) {
                    _result = recordDataUpdataHandle.updateJsonbForElementInsert(query, "chart", v, 0);
                } else {
                    if (chart instanceof List) {
                        List<JSONObject> _list = (List<JSONObject>) chart;
                        if (_list.size() > 0) {
                            //????????????
                            int pos = -1;
                            for (int x = 0; x < _list.size(); x++) {
                                if (_list.get(x).containsKey("chart_id")) {
                                    if (_list.get(x).get("chart_id").equals(cid)) {
                                        pos = x;
                                        break;
                                    }
                                }
                            }
                            if (pos > -1) {
                                if (op.equals("xy") || op.equals("wh") || op.equals("update")) {
                                    //xy ??????  wh ??????  ?????? update
                                    //??????v??????key????????????jfgridfile[i].chart[v.key1] = v.value1
                                    if (v != null) {
                                        JSONObject _s = _list.get(pos);
                                        _s.putAll(v);
                                        _result = recordDataUpdataHandle.updateCellDataListValue(query, "chart", String.valueOf(pos), _s);
                                    }
                                } else if (op.equals("del")) {
                                    _list.remove(pos);
                                    _result = recordDataUpdataHandle.updateCellDataListValue(query, "chart", null, chart);
                                }
                            }
                        }
                    }
                }
            }
            if (!_result) {
                return "????????????";
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    public void Operation_mv(String gridKey, JSONObject bson) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String i = bson.get("i").toString();//	??????sheet???index???
                    String v = bson.get("v").toString();  //	??????????????? v=null ???????????????
                    log.info("Operation_mv---v" + v);
                    Object db = bson.get("v");
                    //???????????????????????????
                    //Query query = new Query();
                    //query.addCriteria(Criteria.where("list_id").is(gridKey).and("index").is(i).and("block_id").is(JfGridConfigModel.FirstBlockID));
                    JSONObject query = getQuery(gridKey, i, JfGridConfigModel.FirstBlockID);
                    boolean _result = recordDataUpdataHandle.updateCellDataListValue(query, "jfgird_select_save", null, db);
                    if (!_result) {
                        log.info("????????????");
                    }
                } catch (Exception e) {
                    log.warn(e.getMessage());
                }
            }
        }).start();
    }

    //3.1 ?????????????????????v
    private void Operation_rv(String gridKey, JSONObject bson) {
        if (GzipHandle.runGzip) {

            return;
        }
        //???????????????
        try {
            log.info("start---Operation_bv" + bson.toString());
            String i = bson.get("i").toString();//	??????sheet???index???
            JSONObject range = JfGridFileUtil.getJSONObjectByIndex(bson, "range");
            List columns = (List) range.get("column");
            List rows = (List) range.get("row");
            Integer r = Integer.parseInt(rows.get(0).toString());//	??????????????????
            int c = Integer.parseInt(columns.get(0).toString());//	??????????????????
            Object all = bson.get("v");  //	??????????????? v=null ???????????????

            //????????????
            String rowCol = recordSelectHandle.getFirstBlockRowColByGridKey(gridKey, i);

            //??????????????????????????????
            Integer isHave = recordSelectHandle.getFirstBlockByGridKey(gridKey, i);
            log.info("isHave---Operation_bv {}", isHave);
            if (isHave == null || isHave == 0) {
                log.error("list_id=" + gridKey + ",index=" + i + "???sheet?????????");
            }
            //???????????????
            HashMap<String, JSONObject> _existsBlock = new HashMap<String, JSONObject>();
            //???????????????
            HashMap<String, JSONObject> _noExistsBlock = new HashMap<String, JSONObject>();
            JSONArray data = (JSONArray) all;
            for (Object datum : data) {
                JSONArray arrayList = (JSONArray) datum;
                int cl = c;
                for (Object v : arrayList) {
                    //??????????????????????????????
                    String block_id = JfGridConfigModel.getRange(r, cl, rowCol);
                    boolean isExists = false;
                    JSONObject _dbObject = null;
                    if (_existsBlock.containsKey(block_id)) {
                        //db????????????????????????????????????
                        isExists = true;
                        _dbObject = _existsBlock.get(block_id);
                    } else if (_noExistsBlock.containsKey(block_id)) {
                        //db????????????
                        _dbObject = _noExistsBlock.get(block_id);
                    } else {
                        //?????????????????????
                        //1?????????????????????????????????????????????sheet???
                        _dbObject = recordSelectHandle.getCelldataByGridKey(gridKey, i, block_id);
                        if (_dbObject == null) {
                            //???????????????????????????
                            //??????
                            JSONArray _celldata = new JSONArray();
                            //??????
                            JSONObject db = new JSONObject();
                            db.put("celldata", _celldata);
                            db.put("block_id", block_id);//??????sheet????????????
                            db.put("index", i); //??????sheet?????????
                            db.put("list_id", gridKey);//????????????
                            _noExistsBlock.put(block_id, db);
                            _dbObject = db;
                        } else {
                            //?????????
                            isExists = true;
                            _existsBlock.put(block_id, _dbObject);
                        }
                    }
                    //?????????????????????????????????????????????
                    if (v != null) {
                        //??????/??????
                        JSONObject _v = new JSONObject();
                        _v.put("r", r);
                        _v.put("c", cl);
                        _v.put("v", v);

                        if (isExists) {
                            //????????????
                            int _position = -1;//???????????????????????????
                            JSONArray _celldata = JfGridFileUtil.getSheetByIndex(_dbObject);
                            if (_celldata != null && _celldata.size() > 0) {
                                int _total = _celldata.size();
                                for (int y = 0; y < _total; y++) {
                                    JSONObject _b = _celldata.getJSONObject(y);
                                    if (_b.get("r").toString().equals(r + "") && _b.get("c").toString().equals(cl + "")) {
                                        _b.put("v", v);
                                        _position = y;
                                        break;
                                    }
                                }
                            }
                            if (_position == -1) {
                                assert _celldata != null;
                                _celldata.add(_v);
                            }
                        } else {
                            //????????????????????????????????????????????????????????????
                            JSONArray _celldata = JfGridFileUtil.getSheetByIndex(_dbObject);
                            _celldata.add(_v);
                        }
                    } else {
                        //??????
                        if (isExists) {
                            //??????????????????
                            int _position = -1;//???????????????????????????
                            JSONArray _celldata = JfGridFileUtil.getSheetByIndex(_dbObject);
                            if (_celldata != null && _celldata.size() > 0) {
                                int _total = _celldata.size();
                                for (int y = 0; y < _total; y++) {
                                    JSONObject _b = _celldata.getJSONObject(y);
                                    if (_b.get("r").toString().equals(r + "") && _b.get("c").toString().equals(cl + "")) {
                                        _position = y;
                                        break;
                                    }
                                }
                            }
                            if (_position != -1) {
                                _celldata.remove(_position);
                            }
                        }
                    }
                    cl++;
                }
                r++;
            }
            //??????
            log.info("_existsBlock--" + _existsBlock.size() + ",_noExistsBlock:" + _noExistsBlock.size());
            List<GridRecordDataModel> models = new ArrayList<>();
            List<String> block_ids = new ArrayList<>();
            if (_existsBlock.size() > 0) {
                for (String _block : _existsBlock.keySet()) {
                    block_ids.add(_block);
                    GridRecordDataModel model = new GridRecordDataModel();
                    JSONObject _bson = _existsBlock.get(_block);
                    JSONArray _celldata = JfGridFileUtil.getSheetByIndex(_bson);
                    model.setBlock_id(_block);
                    model.setIndex(i);
                    model.setList_id(gridKey);
                    JSONObject json_data = new JSONObject();
                    json_data.put("celldata", _celldata);
                    model.setJson_data(json_data);
                    model.setStatus(0);
                    model.setIs_delete(0);
                    models.add(model);
                }
            }
            if (models.size() > 0) {
                boolean _result = recordDataUpdataHandle.batchUpdateCellDataValue(block_ids, models);
                if (!_result) {
                    log.error("????????????");
                }
            }
            List<GridRecordDataModel> isModels = new ArrayList<>();
            if (_noExistsBlock.size() > 0) {
                for (JSONObject _d : _noExistsBlock.values()) {
                    GridRecordDataModel model = new GridRecordDataModel();
                    model.setBlock_id(_d.get("block_id").toString());
                    model.setIndex(_d.get("index").toString());
                    model.setList_id(_d.get("list_id").toString());
                    JSONArray DB = _d.getJSONArray("celldata");
                    JSONObject json_data = new JSONObject();
                    json_data.put("celldata", DB);
                    model.setJson_data(json_data);
                    model.setStatus(0);
                    model.setIs_delete(0);
                    isModels.add(model);
                }
                JSONObject db = recordSelectHandle.getCelldataByGridKey(gridKey, i, JfGridConfigModel.FirstBlockID);
                int col = Integer.parseInt(db.get("column").toString());
                int row = Integer.parseInt(db.get("row").toString());
                if (r < row && c < col) {
                    // todo ???????????????????????????????????????????????????????????????
                } else {
                    Integer updateRow = Math.max(r, row);
                    Integer updateCol = Math.max(c, col);

                    //Query query = new Query();
                    //query.addCriteria(Criteria.where("list_id").is(gridKey).and("index").is(i).and("block_id").is(JfGridConfigModel.FirstBlockID));
                    JSONObject query = getQuery(gridKey, i, JfGridConfigModel.FirstBlockID);

                    JSONObject b = new JSONObject();
                    b.put("row", updateRow);
                    b.put("column", updateCol);
                    boolean result = recordDataUpdataHandle.updateJsonbDataForKeys(query, b);
                    log.info("???????????????????????????" + result);
                }
                String _result = recordDataInsertHandle.InsertIntoBatch(isModels);
                if (_result == null) {
                    log.error("????????????");
                }
            }
            log.info("????????????????????????--end");
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
    }

    //3.7.3	?????????????????????shre
    private String Operation_shre(String gridKey, JSONObject bson) {
        try {
            String reIndex = null;//	???????????????sheet??????
            if (bson.containsKey("v")) {
                JSONObject _v = bson.getJSONObject("v");
                if (_v.containsKey("reIndex")) {
                    reIndex = _v.get("reIndex").toString();
                }
            }
            if (reIndex == null) {
                return "????????????";
            }

            //1?????????????????????
            List<JSONObject> _dbObject = recordSelectHandle.getBlocksByGridKey(gridKey, false);
            if (_dbObject == null) {
                return "gridKey=" + gridKey + "????????????????????????";
            }
            //2??????????????????sheet?????????
            //Integer _sheetPosition=JfGridFileUtil.getSheetPositionByIndex(_dbObject,deleIndex);
            //if(_sheetPosition==null)
            GridRecordDataModel model = new GridRecordDataModel();
            model.setIndex(reIndex);
            model.setList_id(gridKey);
            model.setIs_delete(0);
            boolean result = recordDelHandle.updateDataForReDel(model);
            if (!result) {
                return "????????????";
            }

        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    /**
     * ?????????????????????
     *
     * @param gridKey
     * @param bson
     * @return
     */
    public String getIndexRvForThread(String gridKey, JSONObject bson) {
        log.info("getIndexForRvByThread--start");
        String i = bson.get("i").toString();//	??????sheet???index???
        String key = gridKey + i;
        gridFileRedisCacheService.raddDbContent(key, bson);
        return "";
    }

    public void updateRvDbContent(String gridKey, JSONObject bson, String key) {
        List<JSONObject> bsons = gridFileRedisCacheService.rgetDbDataContent(key);
        loadRvMsgForLock(gridKey, bsons, key);
    }

    private void loadRvMsgForLock(String gridKey, List<JSONObject> bsons, String key) {
        RedisLock redisLock = new RedisLock(redisTemplate, key);
        try {
            if (redisLock.lock()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (JSONObject dbObject : bsons) {
                            Operation_rv(gridKey, dbObject);
                        }
                    }
                }).start();
            } else {
                Thread.sleep(100);
                loadRvMsgForLock(gridKey, bsons, key);
            }
        } catch (Exception ignored) {
        } finally {
            redisLock.unlock();
        }
    }

    /**
     * ?????????????????????
     */
    public void initTestData() {
        List<String> listName = new ArrayList<String>(2) {{
            add("1079500#-8803#7c45f52b7d01486d88bc53cb17dcd2xc");
            add("1079500#-8803#7c45f52b7d01486d88bc53cb17dcd2c3");
        }};
        initTestData(listName);
    }

    public void initTestData(List<String> listName) {
        //int delCount=pgGridFileDao.deleteAll();
        //log.info("del row:{}",delCount);
        int[] delCount = recordDelHandle.delete(listName);
        log.info("del row:{}", delCount);
        List<GridRecordDataModel> models = new ArrayList<>(6);
        //List<String> listName=new ArrayList<String>(2){{
        //    add("1079500#-8803#7c45f52b7d01486d88bc53cb17dcd2xc");
        //    add("1079500#-8803#7c45f52b7d01486d88bc53cb17dcd2c3");
        //}};
        for (String n : listName) {
            for (int x = 0; x < 3; x++) {
                if (x == 0) {
                    models.add(strToModel(n, (x + 1) + "", 1, x));
                } else {
                    models.add(strToModel(n, (x + 1) + "", 0, x));
                }
            }
        }
        String result = insert(models);
        log.info(result);
    }

    private JSONObject getQuery(String gridKey, String i, String blockId) {
        JSONObject query = new JSONObject();
        query.put("list_id", gridKey);
        query.put("index", i);
        query.put("block_id", blockId);
        return query;
    }

    /**
     * @param docCode   ??????code
     * @param modelList ????????????
     * @description ?????????????????????
     * @author zhouhang
     * @date 2021/4/22
     */
    public void initImportExcel(List<GridRecordDataModel> modelList, String docCode) {
        int index = 1;
        List<GridRecordDataModel> addList = new ArrayList<>();
        for (GridRecordDataModel model : modelList) {
            model.setList_id(docCode);
            if (CollectionUtils.isNotEmpty(model.getDataList())) {
                Map<String, List<JSONObject>> map = new HashMap<>(model.getDataList().size() / 5);
                for (JSONObject data : model.getDataList()) {
                    String blockId = JfGridConfigModel.getRange(data.getIntValue("r"), data.getIntValue("c"), model.getRow_col());
                    List<JSONObject> list = map.get(blockId);
                    if (Objects.isNull(list)) {
                        list = new ArrayList<>();
                        list.add(data);
                        map.put(blockId, list);
                    } else {
                        list.add(data);
                    }
                }
                //??????GridRecordDataModel??????
                for (Map.Entry<String, List<JSONObject>> entry : map.entrySet()) {
                    GridRecordDataModel newDataModel = new GridRecordDataModel();
                    newDataModel.setList_id(docCode);
                    newDataModel.setBlock_id(entry.getKey());
                    newDataModel.setIndex(index + "");
                    newDataModel.setStatus(0);
                    newDataModel.setIs_delete(DisabledTypeEnum.ENABLE.getIndex());
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("celldata", entry.getValue());
                    newDataModel.setJson_data(jsonObject);
                    addList.add(newDataModel);
                }
            }
            index++;
        }
        //????????????
        addList.addAll(modelList);
        recordDataInsertHandle.InsertIntoBatch(addList);
    }
}
