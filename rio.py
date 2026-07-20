import sys
mode = sys.argv[1]; path = sys.argv[2]
if mode=="replace":
    old=sys.argv[3]; new=sys.argv[4]
    s=open(path,encoding="utf-8").read()
    if old not in s:
        print("ERROR notfound",path); sys.exit(2)
    open(path,"w",encoding="utf-8").write(s.replace(old,new))
    print("ok replace",path)
