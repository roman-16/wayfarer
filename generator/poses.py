import json

FLAT = -22
HEAD = -20
SHOULDER = -20
HIP = -10
TRUNK = 12
SHOULDER_DROP = 2
SEAT = -10.5


def bone(rotation, position=(0, 0, 0)):
    return {"position": list(position), "rotation": list(rotation)}


def on_back(right_arm, left_arm, right_leg=(-90, 8, 0), left_leg=(-90, -8, 0), head=(-90, 0, 0)):
    return {
        "Body": bone((-90, 0, 0), (0, FLAT, 0)),
        "Head": bone(head, (0, HEAD, 0)),
        "LeftArm": bone(left_arm, (0, SHOULDER, -SHOULDER_DROP)),
        "LeftLeg": bone(left_leg, (0, HIP, -TRUNK)),
        "RightArm": bone(right_arm, (0, SHOULDER, -SHOULDER_DROP)),
        "RightLeg": bone(right_leg, (0, HIP, -TRUNK)),
    }


def on_face(right_arm, left_arm, right_leg=(90, 10, 0), left_leg=(90, -6, 0), head=(90, 0, 60)):
    return {
        "Body": bone((90, 0, 0), (0, FLAT, 0)),
        "Head": bone(head, (0, HEAD, 0)),
        "LeftArm": bone(left_arm, (0, SHOULDER, SHOULDER_DROP)),
        "LeftLeg": bone(left_leg, (0, HIP, TRUNK)),
        "RightArm": bone(right_arm, (0, SHOULDER, SHOULDER_DROP)),
        "RightLeg": bone(right_leg, (0, HIP, TRUNK)),
    }


def on_side(
    lower_arm=(-90, 0, -90), upper_arm=(-24, 0, -90), lower_leg=(-28, 0, -90), upper_leg=(-48, 0, -90), head=(0, 0, -84)
):
    return {
        "Body": bone((0, 0, -90), (0, HEAD, 0)),
        "Head": bone(head, (0, HEAD, 0)),
        "LeftArm": bone(upper_arm, (-3, -13, -1)),
        "LeftLeg": bone(upper_leg, (10, -6, 0)),
        "RightArm": bone(lower_arm, (7, -19, -3)),
        "RightLeg": bone(lower_leg, (14, -10, 0)),
    }


def seated(
    head=(0, 0, 0),
    body=(0, 0, 0),
    right_arm=(-35, 0, 0),
    left_arm=(-35, 0, 0),
    right_leg=(-90, 15, 0),
    left_leg=(-90, -10, 0),
    lean=0,
):
    return {
        "Body": bone(body, (0, SEAT, lean)),
        "Head": bone(head, (0, SEAT, lean)),
        "LeftArm": bone(left_arm, (0, SEAT, lean)),
        "LeftLeg": bone(left_leg, (0, HIP, 0)),
        "RightArm": bone(right_arm, (0, SEAT, lean)),
        "RightLeg": bone(right_leg, (0, HIP, 0)),
    }


def standing(
    head=(0, 0, 0), body=(0, 0, 0), right_arm=(0, 0, 0), left_arm=(0, 0, 0), right_leg=(0, 0, 0), left_leg=(0, 0, 0)
):
    return {
        "Body": bone(body),
        "Head": bone(head),
        "LeftArm": bone(left_arm),
        "LeftLeg": bone(left_leg),
        "RightArm": bone(right_arm),
        "RightLeg": bone(right_leg),
    }


POSES = {
    "fallen_back": on_back((-90, 32, 0), (-90, -24, 0)),
    "fallen_back_knee": on_back((-90, 12, 0), (-90, -48, 0), right_leg=(-56, 6, 0), head=(-90, 0, 24)),
    "fallen_face": on_face((-90, 16, 0), (-90, -24, 0)),
    "fallen_face_still": on_face(
        (90, -8, 0), (90, 8, 0), right_leg=(90, 4, 0), left_leg=(90, -4, 0), head=(90, 0, -64)
    ),
    "fallen_reaching": on_back((90, -24, 0), (-90, -40, 0), (-90, 14, 0), (-90, -4, 0)),
    "fallen_side": on_side(),
    "fallen_side_open": on_side(
        lower_arm=(-90, 32, -90), upper_arm=(-80, 0, -90), lower_leg=(-8, 0, -90), upper_leg=(-16, 0, -90)
    ),
    "fallen_slumped": seated(head=(32, 0, 28), body=(8, 0, 12), right_arm=(-8, 0, 20), left_arm=(-12, 0, -16), lean=-1),
    "laid_out": on_back((-90, 4, 0), (-90, -4, 0), right_leg=(-90, 2, 0), left_leg=(-90, -2, 0)),
    "sitting_crossed": seated(
        right_arm=(-40, 8, 0), left_arm=(-40, -8, 0), right_leg=(-90, -32, 0), left_leg=(-90, 32, 0)
    ),
    "sitting_grieving": seated(
        head=(40, 0, 0), body=(24, 0, 0), right_arm=(-96, -28, 0), left_arm=(-96, 28, 0), lean=-3
    ),
    "sitting_hunched": seated(
        head=(24, 0, 0), body=(16, 0, 0), right_arm=(-56, 12, 0), left_arm=(-56, -12, 0), lean=-2
    ),
    "sitting_leaning": seated(head=(-8, 0, 0), body=(-20, 0, 0), right_arm=(24, 0, 12), left_arm=(24, 0, -12), lean=3),
    "standing_aiming": standing(head=(4, 8, 0), right_arm=(-88, -8, 0), left_arm=(-88, 36, 0)),
    "standing_arms_crossed": standing(right_arm=(-76, -44, 0), left_arm=(-70, 44, 0)),
    "standing_behind_back": standing(head=(-4, 0, 0), right_arm=(24, 0, 8), left_arm=(24, 0, -8)),
    "standing_bowing": standing(head=(52, 0, 0), body=(18, 0, 0), right_arm=(-20, 0, 4), left_arm=(-20, 0, -4)),
    "standing_head_down": standing(head=(36, 0, 0), body=(6, 0, 0), right_arm=(-12, 0, 4), left_arm=(-12, 0, -4)),
    "standing_looking_up": standing(head=(-28, 12, 0), right_arm=(4, 0, 6), left_arm=(-4, 0, -6)),
    "standing_weight": standing(
        head=(0, -8, 6),
        body=(0, 0, 4),
        right_arm=(-6, 0, 6),
        left_arm=(4, 0, -4),
        right_leg=(-6, 0, 4),
        left_leg=(4, 0, -2),
    ),
}
DEAD = [
    "fallen_back",
    "fallen_back_knee",
    "fallen_face",
    "fallen_face_still",
    "fallen_reaching",
    "fallen_side",
    "fallen_side_open",
    "fallen_slumped",
    "sleeping",
]


EASY_NPC_LYING = {"sleeping": "along"}


def lies(bones):
    pitch, _, roll = bones["Body"]["rotation"]
    if abs(pitch) == 90:
        return "along"
    if abs(roll) == 90:
        return "across"
    return None


def write(main):
    lying = {name: lies(bones) for name, bones in POSES.items() if lies(bones)} | EASY_NPC_LYING
    index = main / "wayfarer/lying.json"
    index.parent.mkdir(parents=True, exist_ok=True)
    index.write_text(json.dumps(dict(sorted(lying.items())), indent=2) + "\n")
    for name, bones in POSES.items():
        path = main / f"data/wayfarer/poses/humanoid/{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        animation = {"bones": bones, "loop": "hold_on_last_frame"}
        path.write_text(json.dumps({"animations": {name: animation}, "format_version": "1.8.0"}, indent=2) + "\n")
