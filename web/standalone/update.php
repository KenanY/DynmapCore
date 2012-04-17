<?php
include 'dynmap_access.php';

$world = $_REQUEST['world'];

session_start();

if(isset($_SESSION['userid'])) {
  $userid = $_SESSION['userid'];
}
else {
  $userid = '-guest-';
}

$loggedin = false;
if(strcmp($userid, '-guest-')) {
  $loggedin = true;
}

header('Content-type: text/plain; charset=utf-8');

if(strpos($world, '/') || strpos($world, '\\')) {
    echo "{ \"error\": \"invalid-world\" }";
    return;
}

$fname = 'updates_' . $world . '.php';
if(!file_exists($fname)) {
    echo "{ \"error\": \"bad-world\" }";
	return;
}

$uid = '[' . strtolower($userid) . ']';

if(isset($worldaccess[$world])) {
    $ss = stristr($worldaccess[$world], $uid);
	if($ss === false) {
	    echo "{ \"error\": \"bad-world\" }";
		return;
	}
}

$lines = file($fname);
array_shift($lines);
array_pop($lines);
$json = json_decode(implode(' ',$lines));


if($json->loginrequired && !$loggedin) {
    echo "{ \"error\": \"login-required\" }";
}
else {
	$json->loggedin = $loggedin;
	if ($json->protected) {
	    $ss = stristr($seeallmarkers, $uid);
		if($ss === false) {
			$pcnt = count($json->players);
			for($i = 0; $i < $pcnt; $i++) {
				$p = $json->players[$i];
				if(strcasecmp($userid, $p->account) != 0) {
					$p->world = "-some-other-bogus-world-";
					$p->x = 0.0;
					$p->y = 64.0;
					$p->z = 0.0;
				}
			}
		}
	}
	echo json_encode($json);
}


 
?>

