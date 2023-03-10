package main

import "fmt"

/*
映射 (`map`)
映射将键映射到值。

映射的零值为 nil 。nil 映射既没有键，也不能添加键。

make 函数会返回给定类型的映射，并将其初始化备用。
*/

type Vertex struct {
	Lat, Long float64
}

var m map[string]Vertex

func main() {

	m = make(map[string]Vertex)
	m["Bell Labs"] = Vertex{
		40.68433, -74.39967,
	}
	fmt.Println(m["Bell Labs"])

	// 初始化一个 map
	mp := map[int]bool{}
	mp[1] = false
	fmt.Println(mp[1])

}
